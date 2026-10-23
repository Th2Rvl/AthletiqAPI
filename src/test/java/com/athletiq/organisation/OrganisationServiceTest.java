package com.athletiq.organisation;

import static com.athletiq.support.TestEntities.invitation;
import static com.athletiq.support.TestEntities.membership;
import static com.athletiq.support.TestEntities.organisation;
import static com.athletiq.support.TestEntities.user;
import static com.athletiq.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.athletiq.common.exception.BusinessRuleException;
import com.athletiq.common.exception.DomainException;
import com.athletiq.common.exception.ErrorCodes;
import com.athletiq.common.exception.ForbiddenActionException;
import com.athletiq.common.exception.InvalidParameterException;
import com.athletiq.common.exception.ResourceNotFoundException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.organisation.dto.CreateOrganisationRequest;
import com.athletiq.organisation.dto.InvitationResponse;
import com.athletiq.organisation.dto.OrganisationResponse;
import com.athletiq.organisation.dto.UpdateOrganisationRequest;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserService;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class OrganisationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    @Mock
    private OrganisationRepository organisationRepository;
    @Mock
    private OrganisationCoachRepository coachRepository;
    @Mock
    private OrganisationInvitationRepository invitationRepository;
    @Mock
    private UserService userService;
    @Mock
    private AccessGuard accessGuard;

    private OrganisationService service;

    @BeforeEach
    void setUp() {
        service = new OrganisationService(organisationRepository, coachRepository, invitationRepository,
                new OrganisationMapperImpl(), userService, accessGuard, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static void assertDomainError(Throwable thrown, Class<? extends DomainException> type, String code) {
        assertThat(thrown).isInstanceOf(type);
        assertThat(((DomainException) thrown).getCode()).isEqualTo(code);
    }

    private static DataIntegrityViolationException duplicateKey(String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry 'x' for key 'table." + constraint + "'"));
    }

    @Nested
    class AccessControl {

        @Test
        void everyAdminOperationIsRefusedToANonAdmin() {
            doThrow(new ForbiddenActionException(ErrorCodes.ACCESS_DENIED, "Accès refusé"))
                    .when(accessGuard).assertAdmin();
            UUID id = UUID.randomUUID();
            Pageable page = PageRequest.of(0, 20);

            List<Runnable> operations = List.of(
                    () -> service.list(null, page),
                    () -> service.get(id),
                    () -> service.create(new CreateOrganisationRequest("Club", null)),
                    () -> service.update(id, new UpdateOrganisationRequest("Club", null)),
                    () -> service.delete(id),
                    () -> service.listCoaches(id, page),
                    () -> service.removeCoach(id, id),
                    () -> service.inviteCoach(id, "coach@club.fr"),
                    () -> service.listInvitations(id, InvitationStatus.PENDING, page),
                    () -> service.getInvitation(id, id),
                    () -> service.cancelInvitation(id, id),
                    () -> service.listAthletes(id, page));

            for (Runnable operation : operations) {
                assertDomainError(catchThrowable(operation), ForbiddenActionException.class, ErrorCodes.ACCESS_DENIED);
            }
            verifyNoInteractions(organisationRepository, coachRepository, invitationRepository, userService);
        }

        private static Throwable catchThrowable(Runnable runnable) {
            try {
                runnable.run();
                return null;
            } catch (RuntimeException ex) {
                return ex;
            }
        }
    }

    @Nested
    class ListOrganisations {

        @Test
        void computesCountersWithGroupedQueries() {
            Organisation rennes = organisation("Stade Rennais Athlétisme");
            Organisation morbihan = organisation("Trail Club du Morbihan");
            Pageable pageable = PageRequest.of(0, 20, Sort.by("name"));
            when(organisationRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(rennes, morbihan), pageable, 2));
            when(coachRepository.countByOrganisationIds(anyCollection()))
                    .thenReturn(List.of(new OrganisationCount(rennes.getId(), 2)));
            when(invitationRepository.countByOrganisationIdsAndStatus(anyCollection(), eq(InvitationStatus.PENDING)))
                    .thenReturn(List.of(new OrganisationCount(rennes.getId(), 1)));

            Page<OrganisationResponse> result = service.list(null, pageable);

            assertThat(result.getContent()).extracting(OrganisationResponse::name, OrganisationResponse::coachCount,
                            OrganisationResponse::pendingInvitationCount, OrganisationResponse::athleteCount)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("Stade Rennais Athlétisme", 2L, 1L, 0L),
                            org.assertj.core.groups.Tuple.tuple("Trail Club du Morbihan", 0L, 0L, 0L));
        }

        @Test
        void searchesByTrimmedQuery() {
            Pageable pageable = PageRequest.of(0, 20, Sort.by("name"));
            when(organisationRepository.findByNameContaining("rennais", pageable)).thenReturn(Page.empty(pageable));

            assertThat(service.list("  rennais ", pageable)).isEmpty();
        }

        @Test
        void refusesSortOnANonAllowedProperty() {
            Pageable pageable = PageRequest.of(0, 20, Sort.by("description"));

            assertThatThrownBy(() -> service.list(null, pageable)).isInstanceOf(InvalidParameterException.class);
            verifyNoInteractions(organisationRepository);
        }
    }

    @Nested
    class Create {

        @Test
        void createsAnOrganisationWithZeroCoach() {
            when(organisationRepository.existsByName("Stade Rennais Athlétisme")).thenReturn(false);
            when(organisationRepository.saveAndFlush(any(Organisation.class)))
                    .thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));

            OrganisationResponse response = service.create(
                    new CreateOrganisationRequest("  Stade Rennais Athlétisme  ", "  Sprint  "));

            assertThat(response.name()).isEqualTo("Stade Rennais Athlétisme");
            assertThat(response.description()).isEqualTo("Sprint");
            assertThat(response.coachCount()).isZero();
            assertThat(response.id()).isNotNull();
        }

        @Test
        void refusesANameAlreadyTaken() {
            when(organisationRepository.existsByName("stade rennais athletisme")).thenReturn(true);

            Throwable thrown = catchThrowable(new CreateOrganisationRequest("stade rennais athletisme", null));

            assertDomainError(thrown, BusinessRuleException.class, OrganisationErrors.ORGANISATION_NAME_TAKEN);
            assertThat(((BusinessRuleException) thrown).getField()).isEqualTo("name");
            verify(organisationRepository, never()).saveAndFlush(any());
        }

        @Test
        void translatesAConcurrentUniqueViolationIntoTheSameBusinessError() {
            when(organisationRepository.existsByName("Club")).thenReturn(false);
            when(organisationRepository.saveAndFlush(any(Organisation.class)))
                    .thenThrow(duplicateKey(OrganisationService.UK_ORGANISATION_NAME));

            assertDomainError(catchThrowable(new CreateOrganisationRequest("Club", null)),
                    BusinessRuleException.class, OrganisationErrors.ORGANISATION_NAME_TAKEN);
        }

        @Test
        void letsAnUnknownIntegrityViolationGoThrough() {
            when(organisationRepository.existsByName("Club")).thenReturn(false);
            when(organisationRepository.saveAndFlush(any(Organisation.class)))
                    .thenThrow(duplicateKey("another_constraint"));

            assertThat(catchThrowable(new CreateOrganisationRequest("Club", null)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        private Throwable catchThrowable(CreateOrganisationRequest request) {
            try {
                service.create(request);
                return null;
            } catch (RuntimeException ex) {
                return ex;
            }
        }
    }

    @Nested
    class Update {

        @Test
        void acceptsItsOwnNameWrittenDifferently() {
            Organisation rennes = organisation("Stade Rennais Athlétisme");
            rennes.setDescription("Ancienne description");
            when(organisationRepository.findById(rennes.getId())).thenReturn(Optional.of(rennes));
            when(organisationRepository.existsByNameAndIdNot("STADE RENNAIS ATHLETISME", rennes.getId()))
                    .thenReturn(false);
            when(organisationRepository.saveAndFlush(rennes)).thenReturn(rennes);

            OrganisationResponse response = service.update(rennes.getId(),
                    new UpdateOrganisationRequest("STADE RENNAIS ATHLETISME", null));

            assertThat(response.name()).isEqualTo("STADE RENNAIS ATHLETISME");
            assertThat(response.description()).as("remplacement complet : description effacée").isNull();
        }

        @Test
        void refusesTheNameOfAnotherOrganisation() {
            Organisation rennes = organisation("Stade Rennais Athlétisme");
            when(organisationRepository.findById(rennes.getId())).thenReturn(Optional.of(rennes));
            when(organisationRepository.existsByNameAndIdNot("Nantes Métropole Athlétisme", rennes.getId()))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.update(rennes.getId(),
                    new UpdateOrganisationRequest("Nantes Métropole Athlétisme", null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("code").isEqualTo(OrganisationErrors.ORGANISATION_NAME_TAKEN);
            assertThat(rennes.getName()).isEqualTo("Stade Rennais Athlétisme");
        }

        @Test
        void refusesAnUnknownOrganisation() {
            UUID id = UUID.randomUUID();
            when(organisationRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(id, new UpdateOrganisationRequest("Club", null)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("code").isEqualTo(OrganisationErrors.ORGANISATION_NOT_FOUND);
        }
    }

    @Nested
    class Delete {

        @Test
        void deletesMembershipsInvitationsThenTheOrganisation() {
            UUID id = UUID.randomUUID();
            when(organisationRepository.existsById(id)).thenReturn(true);

            service.delete(id);

            verify(coachRepository).deleteAllByOrganisationId(id);
            verify(invitationRepository).deleteAllByOrganisationId(id);
            verify(organisationRepository).deleteById(id);
            verifyNoInteractions(userService);
        }

        @Test
        void refusesAnUnknownOrganisation() {
            UUID id = UUID.randomUUID();
            when(organisationRepository.existsById(id)).thenReturn(false);

            assertThatThrownBy(() -> service.delete(id))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("code").isEqualTo(OrganisationErrors.ORGANISATION_NOT_FOUND);
            verify(organisationRepository, never()).deleteById(any());
        }
    }

    @Nested
    class InviteCoach {

        private Organisation rennes;

        @BeforeEach
        void setUp() {
            rennes = organisation("Stade Rennais Athlétisme");
        }

        @Test
        void refusesAnUnknownOrganisation() {
            UUID id = UUID.randomUUID();
            when(organisationRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.inviteCoach(id, "coach@club.fr"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("code").isEqualTo(OrganisationErrors.ORGANISATION_NOT_FOUND);
        }

        @Test
        void attachesAnExistingCoachImmediately() {
            User coach = user("coach@club.fr", Role.COACH);
            when(organisationRepository.findById(rennes.getId())).thenReturn(Optional.of(rennes));
            when(userService.findByEmail("coach@club.fr")).thenReturn(Optional.of(coach));
            when(coachRepository.existsByOrganisationIdAndCoachId(rennes.getId(), coach.getId())).thenReturn(false);
            when(invitationRepository.findByOrganisationIdAndEmailAndStatus(rennes.getId(), "coach@club.fr",
                    InvitationStatus.PENDING)).thenReturn(Optional.empty());
            when(invitationRepository.saveAndFlush(any(OrganisationInvitation.class)))
                    .thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));

            InvitationResponse response = service.inviteCoach(rennes.getId(), "coach@club.fr");

            ArgumentCaptor<OrganisationCoach> membership = ArgumentCaptor.forClass(OrganisationCoach.class);
            verify(coachRepository).saveAndFlush(membership.capture());
            assertThat(membership.getValue().getCoach()).isSameAs(coach);
            assertThat(membership.getValue().getJoinedAt()).isEqualTo(NOW);
            assertThat(response.status()).isEqualTo(InvitationStatus.ACCEPTED);
            assertThat(response.coachId()).isEqualTo(coach.getId());
            assertThat(response.organisationId()).isEqualTo(rennes.getId());
        }

        @Test
        void convertsAnExistingPendingInvitationWhenTheCoachHasAnAccount() {
            User coach = user("coach@club.fr", Role.COACH);
            OrganisationInvitation pending = invitation(rennes, "coach@club.fr", InvitationStatus.PENDING);
            when(organisationRepository.findById(rennes.getId())).thenReturn(Optional.of(rennes));
            when(userService.findByEmail("coach@club.fr")).thenReturn(Optional.of(coach));
            when(coachRepository.existsByOrganisationIdAndCoachId(rennes.getId(), coach.getId())).thenReturn(false);
            when(invitationRepository.findByOrganisationIdAndEmailAndStatus(rennes.getId(), "coach@club.fr",
                    InvitationStatus.PENDING)).thenReturn(Optional.of(pending));
            when(invitationRepository.saveAndFlush(pending)).thenReturn(pending);

            InvitationResponse response = service.inviteCoach(rennes.getId(), "coach@club.fr");

            assertThat(response.id()).isEqualTo(pending.getId());
            assertThat(pending.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
            assertThat(pending.getCoach()).isSameAs(coach);
        }

        @Test
        void refusesAnAccountThatIsNotACoach() {
            for (Role role : List.of(Role.ADMIN, Role.ATHLETE)) {
                User account = user("lucas.girard@athlete.fr", role);
                when(organisationRepository.findById(rennes.getId())).thenReturn(Optional.of(rennes));
                when(userService.findByEmail("lucas.girard@athlete.fr")).thenReturn(Optional.of(account));

                assertThatThrownBy(() -> service.inviteCoach(rennes.getId(), "lucas.girard@athlete.fr"))
                        .isInstanceOf(BusinessRuleException.class)
                        .extracting("code", "field").containsExactly(OrganisationErrors.ACCOUNT_NOT_COACH, "email");
            }
            verify(coachRepository, never()).saveAndFlush(any());
            verify(invitationRepository, never()).saveAndFlush(any());
        }

        @Test
        void refusesACoachAlreadyMember() {
            User coach = user("coach@club.fr", Role.COACH);
            when(organisationRepository.findById(rennes.getId())).thenReturn(Optional.of(rennes));
            when(userService.findByEmail("coach@club.fr")).thenReturn(Optional.of(coach));
            when(coachRepository.existsByOrganisationIdAndCoachId(rennes.getId(), coach.getId())).thenReturn(true);

            assertThatThrownBy(() -> service.inviteCoach(rennes.getId(), "coach@club.fr"))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("code").isEqualTo(OrganisationErrors.COACH_ALREADY_MEMBER);
            verify(coachRepository, never()).saveAndFlush(any());
        }

        @Test
        void translatesAConcurrentMembershipIntoCoachAlreadyMember() {
            User coach = user("coach@club.fr", Role.COACH);
            when(organisationRepository.findById(rennes.getId())).thenReturn(Optional.of(rennes));
            when(userService.findByEmail("coach@club.fr")).thenReturn(Optional.of(coach));
            when(coachRepository.existsByOrganisationIdAndCoachId(rennes.getId(), coach.getId())).thenReturn(false);
            when(coachRepository.saveAndFlush(any(OrganisationCoach.class)))
                    .thenThrow(duplicateKey(OrganisationService.UK_ORGANISATION_COACH));

            assertThatThrownBy(() -> service.inviteCoach(rennes.getId(), "coach@club.fr"))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("code").isEqualTo(OrganisationErrors.COACH_ALREADY_MEMBER);
        }

        @Test
        void createsAPendingInvitationWhenNoAccountExists() {
            when(organisationRepository.findById(rennes.getId())).thenReturn(Optional.of(rennes));
            when(userService.findByEmail("nouveau@club.fr")).thenReturn(Optional.empty());
            when(invitationRepository.existsByOrganisationIdAndEmailAndStatus(rennes.getId(), "nouveau@club.fr",
                    InvitationStatus.PENDING)).thenReturn(false);
            when(invitationRepository.saveAndFlush(any(OrganisationInvitation.class)))
                    .thenAnswer(call -> withId(call.getArgument(0), UUID.randomUUID()));

            InvitationResponse response = service.inviteCoach(rennes.getId(), "nouveau@club.fr");

            assertThat(response.status()).isEqualTo(InvitationStatus.PENDING);
            assertThat(response.email()).isEqualTo("nouveau@club.fr");
            assertThat(response.coachId()).isNull();
            verify(coachRepository, never()).saveAndFlush(any());
        }

        @Test
        void refusesASecondPendingInvitation() {
            when(organisationRepository.findById(rennes.getId())).thenReturn(Optional.of(rennes));
            when(userService.findByEmail("nouveau@club.fr")).thenReturn(Optional.empty());
            when(invitationRepository.existsByOrganisationIdAndEmailAndStatus(rennes.getId(), "nouveau@club.fr",
                    InvitationStatus.PENDING)).thenReturn(true);

            assertThatThrownBy(() -> service.inviteCoach(rennes.getId(), "nouveau@club.fr"))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("code", "field")
                    .containsExactly(OrganisationErrors.INVITATION_ALREADY_PENDING, "email");
            verify(invitationRepository, never()).saveAndFlush(any());
        }

        @Test
        void translatesAConcurrentPendingInvitation() {
            when(organisationRepository.findById(rennes.getId())).thenReturn(Optional.of(rennes));
            when(userService.findByEmail("nouveau@club.fr")).thenReturn(Optional.empty());
            when(invitationRepository.existsByOrganisationIdAndEmailAndStatus(rennes.getId(), "nouveau@club.fr",
                    InvitationStatus.PENDING)).thenReturn(false);
            when(invitationRepository.saveAndFlush(any(OrganisationInvitation.class)))
                    .thenThrow(duplicateKey(OrganisationService.UK_INVITATION_PENDING));

            assertThatThrownBy(() -> service.inviteCoach(rennes.getId(), "nouveau@club.fr"))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("code").isEqualTo(OrganisationErrors.INVITATION_ALREADY_PENDING);
        }
    }

    @Nested
    class RemoveCoach {

        @Test
        void deletesOnlyTheMembership() {
            Organisation rennes = organisation("Stade Rennais Athlétisme");
            User coach = user("coach@club.fr", Role.COACH);
            OrganisationCoach membership = membership(rennes, coach);
            when(organisationRepository.existsById(rennes.getId())).thenReturn(true);
            when(coachRepository.findByOrganisationIdAndCoachId(rennes.getId(), coach.getId()))
                    .thenReturn(Optional.of(membership));

            service.removeCoach(rennes.getId(), coach.getId());

            verify(coachRepository).delete(membership);
            verifyNoInteractions(userService);
        }

        @Test
        void refusesACoachWhoIsNotMember() {
            UUID organisationId = UUID.randomUUID();
            UUID coachId = UUID.randomUUID();
            when(organisationRepository.existsById(organisationId)).thenReturn(true);
            when(coachRepository.findByOrganisationIdAndCoachId(organisationId, coachId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.removeCoach(organisationId, coachId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("code").isEqualTo(OrganisationErrors.COACH_NOT_MEMBER);
        }

        @Test
        void refusesAnUnknownOrganisation() {
            UUID organisationId = UUID.randomUUID();
            when(organisationRepository.existsById(organisationId)).thenReturn(false);

            assertThatThrownBy(() -> service.removeCoach(organisationId, UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("code").isEqualTo(OrganisationErrors.ORGANISATION_NOT_FOUND);
            verifyNoInteractions(coachRepository);
        }
    }

    @Nested
    class Invitations {

        private final Organisation rennes = organisation("Stade Rennais Athlétisme");

        @Test
        void cancelsAPendingInvitation() {
            OrganisationInvitation pending = invitation(rennes, "nouveau@club.fr", InvitationStatus.PENDING);
            when(organisationRepository.existsById(rennes.getId())).thenReturn(true);
            when(invitationRepository.findByIdAndOrganisationId(pending.getId(), rennes.getId()))
                    .thenReturn(Optional.of(pending));

            service.cancelInvitation(rennes.getId(), pending.getId());

            assertThat(pending.getStatus()).isEqualTo(InvitationStatus.CANCELLED);
            verify(invitationRepository, never()).delete(any());
        }

        @Test
        void refusesToCancelAnInvitationThatIsNoLongerPending() {
            OrganisationInvitation accepted = invitation(rennes, "coach@club.fr", InvitationStatus.ACCEPTED);
            when(organisationRepository.existsById(rennes.getId())).thenReturn(true);
            when(invitationRepository.findByIdAndOrganisationId(accepted.getId(), rennes.getId()))
                    .thenReturn(Optional.of(accepted));

            assertThatThrownBy(() -> service.cancelInvitation(rennes.getId(), accepted.getId()))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("code").isEqualTo(OrganisationErrors.INVITATION_NOT_PENDING);
            assertThat(accepted.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        }

        @Test
        void treatsAnInvitationOfAnotherOrganisationAsNotFound() {
            UUID invitationId = UUID.randomUUID();
            when(organisationRepository.existsById(rennes.getId())).thenReturn(true);
            when(invitationRepository.findByIdAndOrganisationId(invitationId, rennes.getId()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelInvitation(rennes.getId(), invitationId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting("code").isEqualTo(OrganisationErrors.INVITATION_NOT_FOUND);
        }

        @Test
        void listsPendingInvitationsByDefaultMostRecentFirst() {
            when(organisationRepository.existsById(rennes.getId())).thenReturn(true);
            when(invitationRepository.findByOrganisationIdAndStatus(eq(rennes.getId()), eq(InvitationStatus.PENDING),
                    any(Pageable.class))).thenReturn(Page.empty());

            service.listInvitations(rennes.getId(), null, PageRequest.of(0, 20, Sort.by("email")));

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(invitationRepository).findByOrganisationIdAndStatus(eq(rennes.getId()),
                    eq(InvitationStatus.PENDING), pageable.capture());
            assertThat(pageable.getValue().getSort().getOrderFor("createdAt")).isNotNull()
                    .extracting(Sort.Order::getDirection).isEqualTo(Sort.Direction.DESC);
            assertThat(pageable.getValue().getSort().getOrderFor("email")).as("sort du client ignoré").isNull();
        }
    }

    @Nested
    class Athletes {

        @Test
        void returnsAnEmptyPageUntilTheCoachAthleteRelationExists() {
            UUID id = UUID.randomUUID();
            when(organisationRepository.existsById(id)).thenReturn(true);

            assertThat(service.listAthletes(id, PageRequest.of(0, 20))).isEmpty();
        }

        @Test
        void refusesAnUnknownOrganisation() {
            UUID id = UUID.randomUUID();
            when(organisationRepository.existsById(id)).thenReturn(false);

            assertThatThrownBy(() -> service.listAthletes(id, PageRequest.of(0, 20)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class ConvertPendingInvitations {

        @Test
        void turnsPendingInvitationsIntoMemberships() {
            User coach = user("nouveau@club.fr", Role.COACH);
            Organisation rennes = organisation("Stade Rennais Athlétisme");
            Organisation nantes = organisation("Nantes Métropole Athlétisme");
            OrganisationInvitation toRennes = invitation(rennes, "nouveau@club.fr", InvitationStatus.PENDING);
            OrganisationInvitation toNantes = invitation(nantes, "nouveau@club.fr", InvitationStatus.PENDING);
            when(invitationRepository.findAllByEmailAndStatus("nouveau@club.fr", InvitationStatus.PENDING))
                    .thenReturn(List.of(toRennes, toNantes));
            when(coachRepository.existsByOrganisationIdAndCoachId(rennes.getId(), coach.getId())).thenReturn(false);
            when(coachRepository.existsByOrganisationIdAndCoachId(nantes.getId(), coach.getId())).thenReturn(true);

            int created = service.convertPendingInvitations(coach);

            assertThat(created).isEqualTo(1);
            assertThat(List.of(toRennes, toNantes)).allSatisfy(invitation -> {
                assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
                assertThat(invitation.getCoach()).isSameAs(coach);
            });
            verify(coachRepository).save(any(OrganisationCoach.class));
        }

        @Test
        void refusesAnAccountThatIsNotACoach() {
            User athlete = user("lucas.girard@athlete.fr", Role.ATHLETE);

            assertThatThrownBy(() -> service.convertPendingInvitations(athlete))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("code").isEqualTo(OrganisationErrors.ACCOUNT_NOT_COACH);
            verifyNoInteractions(invitationRepository);
        }
    }
}
