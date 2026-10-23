package com.athletiq.organisation;

import com.athletiq.common.exception.InvalidParameterException;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.organisation.dto.CreateOrganisationRequest;
import com.athletiq.organisation.dto.InvitationResponse;
import com.athletiq.organisation.dto.OrganisationAthleteResponse;
import com.athletiq.organisation.dto.OrganisationCoachResponse;
import com.athletiq.organisation.dto.OrganisationResponse;
import com.athletiq.organisation.dto.UpdateOrganisationRequest;
import com.athletiq.user.EmailNormalizer;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserService;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion des organisations, de leurs coachs et de leurs invitations. Réservée à l'administrateur
 * de la plateforme : chaque méthode exposée au controller commence par {@code accessGuard.assertAdmin()}.
 */
@Service
@RequiredArgsConstructor
public class OrganisationService {

    static final String UK_ORGANISATION_NAME = "uk_organisation_name";
    static final String UK_ORGANISATION_COACH = "uk_organisation_coach";
    static final String UK_INVITATION_PENDING = "uk_organisation_invitation_pending";

    private static final Set<String> SORTABLE_PROPERTIES = Set.of("name", "createdAt");
    private static final Sort INVITATION_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final OrganisationRepository organisationRepository;
    private final OrganisationCoachRepository coachRepository;
    private final OrganisationInvitationRepository invitationRepository;
    private final OrganisationMapper mapper;
    private final UserService userService;
    private final AccessGuard accessGuard;
    private final Clock clock;

    // ---------------------------------------------------------------- organisations

    @Transactional(readOnly = true)
    public Page<OrganisationResponse> list(String query, Pageable pageable) {
        accessGuard.assertAdmin();
        assertSortable(pageable.getSort());
        Page<Organisation> page = query == null || query.isBlank()
                ? organisationRepository.findAll(pageable)
                : organisationRepository.findByNameContaining(query.strip(), pageable);
        if (page.isEmpty()) {
            return page.map(organisation -> toResponse(organisation, 0, 0));
        }
        List<UUID> ids = page.getContent().stream().map(Organisation::getId).toList();
        Map<UUID, Long> coachCounts = toMap(coachRepository.countByOrganisationIds(ids));
        Map<UUID, Long> pendingCounts = toMap(
                invitationRepository.countByOrganisationIdsAndStatus(ids, InvitationStatus.PENDING));
        return page.map(organisation -> toResponse(organisation,
                coachCounts.getOrDefault(organisation.getId(), 0L),
                pendingCounts.getOrDefault(organisation.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public OrganisationResponse get(UUID organisationId) {
        accessGuard.assertAdmin();
        return toResponseWithCounts(findOrganisation(organisationId));
    }

    @Transactional
    public OrganisationResponse create(CreateOrganisationRequest request) {
        accessGuard.assertAdmin();
        if (organisationRepository.existsByName(request.name())) {
            throw OrganisationErrors.nameTaken();
        }
        Organisation organisation = Organisation.builder()
                .name(request.name())
                .description(request.description())
                .build();
        Organisation saved = saveAndFlush(() -> organisationRepository.saveAndFlush(organisation));
        return toResponse(saved, 0, 0);
    }

    /** Renommer une organisation avec son propre nom (casse ou accents différents) est autorisé. */
    @Transactional
    public OrganisationResponse update(UUID organisationId, UpdateOrganisationRequest request) {
        accessGuard.assertAdmin();
        Organisation organisation = findOrganisation(organisationId);
        if (organisationRepository.existsByNameAndIdNot(request.name(), organisationId)) {
            throw OrganisationErrors.nameTaken();
        }
        organisation.setName(request.name());
        organisation.setDescription(request.description());
        Organisation saved = saveAndFlush(() -> organisationRepository.saveAndFlush(organisation));
        return toResponseWithCounts(saved);
    }

    /** Supprime l'organisation, ses rattachements et ses invitations. Les comptes ne sont jamais touchés. */
    @Transactional
    public void delete(UUID organisationId) {
        accessGuard.assertAdmin();
        if (!organisationRepository.existsById(organisationId)) {
            throw OrganisationErrors.organisationNotFound();
        }
        coachRepository.deleteAllByOrganisationId(organisationId);
        invitationRepository.deleteAllByOrganisationId(organisationId);
        organisationRepository.deleteById(organisationId);
    }

    // ---------------------------------------------------------------- coachs

    @Transactional(readOnly = true)
    public Page<OrganisationCoachResponse> listCoaches(UUID organisationId, Pageable pageable) {
        accessGuard.assertAdmin();
        assertOrganisationExists(organisationId);
        return coachRepository.findPageByOrganisationId(organisationId, unsorted(pageable))
                .map(mapper::toCoachResponse);
    }

    /** Retire le coach de l'organisation : seul le rattachement est supprimé, le compte est conservé. */
    @Transactional
    public void removeCoach(UUID organisationId, UUID coachId) {
        accessGuard.assertAdmin();
        assertOrganisationExists(organisationId);
        OrganisationCoach membership = coachRepository.findByOrganisationIdAndCoachId(organisationId, coachId)
                .orElseThrow(OrganisationErrors::coachNotMember);
        coachRepository.delete(membership);
    }

    // ---------------------------------------------------------------- invitations

    /**
     * Invite un coach par e-mail. Si un compte coach existe, il est rattaché immédiatement
     * (invitation ACCEPTED) ; sinon une invitation PENDING est créée, sans envoi d'e-mail.
     */
    @Transactional
    public InvitationResponse inviteCoach(UUID organisationId, String email) {
        accessGuard.assertAdmin();
        Organisation organisation = findOrganisation(organisationId);
        String normalizedEmail = EmailNormalizer.normalize(email);
        Optional<User> account = userService.findByEmail(normalizedEmail);
        if (account.isPresent()) {
            return attachExistingCoach(organisation, account.get());
        }
        if (invitationRepository.existsByOrganisationIdAndEmailAndStatus(
                organisationId, normalizedEmail, InvitationStatus.PENDING)) {
            throw OrganisationErrors.invitationAlreadyPending();
        }
        OrganisationInvitation invitation = OrganisationInvitation.builder()
                .organisation(organisation)
                .email(normalizedEmail)
                .status(InvitationStatus.PENDING)
                .build();
        return mapper.toInvitationResponse(saveAndFlush(() -> invitationRepository.saveAndFlush(invitation)));
    }

    @Transactional(readOnly = true)
    public Page<InvitationResponse> listInvitations(UUID organisationId, InvitationStatus status,
                                                    Pageable pageable) {
        accessGuard.assertAdmin();
        assertOrganisationExists(organisationId);
        InvitationStatus effectiveStatus = status == null ? InvitationStatus.PENDING : status;
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), INVITATION_SORT);
        return invitationRepository.findByOrganisationIdAndStatus(organisationId, effectiveStatus, sorted)
                .map(mapper::toInvitationResponse);
    }

    @Transactional(readOnly = true)
    public InvitationResponse getInvitation(UUID organisationId, UUID invitationId) {
        accessGuard.assertAdmin();
        assertOrganisationExists(organisationId);
        return mapper.toInvitationResponse(findInvitation(organisationId, invitationId));
    }

    /** Annule une invitation en attente (passage en CANCELLED, sans suppression physique). */
    @Transactional
    public void cancelInvitation(UUID organisationId, UUID invitationId) {
        accessGuard.assertAdmin();
        assertOrganisationExists(organisationId);
        OrganisationInvitation invitation = findInvitation(organisationId, invitationId);
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw OrganisationErrors.invitationNotPending();
        }
        invitation.setStatus(InvitationStatus.CANCELLED);
    }

    /**
     * Convertit les invitations PENDING adressées à ce coach en rattachements. À brancher sur la
     * création de compte coach (#2).
     *
     * <p>Pas d'{@code assertAdmin()} ici : cette méthode est appelée par le parcours d'inscription,
     * où l'utilisateur courant est le nouveau coach (ou personne). Elle n'est exposée par aucun
     * endpoint et n'agit que sur le compte que lui passe le service appelant.
     *
     * @return le nombre de rattachements créés
     */
    @Transactional
    public int convertPendingInvitations(User coach) {
        if (coach.getRole() != Role.COACH) {
            throw OrganisationErrors.accountNotCoach();
        }
        List<OrganisationInvitation> pending = invitationRepository.findAllByEmailAndStatus(
                EmailNormalizer.normalize(coach.getEmail()), InvitationStatus.PENDING);
        int created = 0;
        for (OrganisationInvitation invitation : pending) {
            Organisation organisation = invitation.getOrganisation();
            if (!coachRepository.existsByOrganisationIdAndCoachId(organisation.getId(), coach.getId())) {
                coachRepository.save(newMembership(organisation, coach));
                created++;
            }
            invitation.accept(coach);
        }
        return created;
    }

    // ---------------------------------------------------------------- athlètes

    /** Les athlètes se déduisent des coachs membres : la page reste vide jusqu'à la relation coach-athlète (#8). */
    @Transactional(readOnly = true)
    public Page<OrganisationAthleteResponse> listAthletes(UUID organisationId, Pageable pageable) {
        accessGuard.assertAdmin();
        assertOrganisationExists(organisationId);
        return Page.empty(unsorted(pageable));
    }

    // ---------------------------------------------------------------- interne

    private InvitationResponse attachExistingCoach(Organisation organisation, User account) {
        if (account.getRole() != Role.COACH) {
            throw OrganisationErrors.accountNotCoach();
        }
        if (coachRepository.existsByOrganisationIdAndCoachId(organisation.getId(), account.getId())) {
            throw OrganisationErrors.coachAlreadyMember();
        }
        OrganisationCoach membership = newMembership(organisation, account);
        saveAndFlush(() -> coachRepository.saveAndFlush(membership));

        OrganisationInvitation invitation = invitationRepository
                .findByOrganisationIdAndEmailAndStatus(organisation.getId(), account.getEmail(),
                        InvitationStatus.PENDING)
                .orElseGet(() -> OrganisationInvitation.builder()
                        .organisation(organisation)
                        .email(account.getEmail())
                        .build());
        invitation.accept(account);
        return mapper.toInvitationResponse(saveAndFlush(() -> invitationRepository.saveAndFlush(invitation)));
    }

    private OrganisationCoach newMembership(Organisation organisation, User coach) {
        return OrganisationCoach.builder()
                .organisation(organisation)
                .coach(coach)
                .joinedAt(clock.instant())
                .build();
    }

    private Organisation findOrganisation(UUID organisationId) {
        return organisationRepository.findById(organisationId)
                .orElseThrow(OrganisationErrors::organisationNotFound);
    }

    private void assertOrganisationExists(UUID organisationId) {
        if (!organisationRepository.existsById(organisationId)) {
            throw OrganisationErrors.organisationNotFound();
        }
    }

    /** Une invitation d'une autre organisation est traitée comme inexistante. */
    private OrganisationInvitation findInvitation(UUID organisationId, UUID invitationId) {
        return invitationRepository.findByIdAndOrganisationId(invitationId, organisationId)
                .orElseThrow(OrganisationErrors::invitationNotFound);
    }

    private OrganisationResponse toResponseWithCounts(Organisation organisation) {
        return toResponse(organisation,
                coachRepository.countByOrganisationId(organisation.getId()),
                invitationRepository.countByOrganisationIdAndStatus(organisation.getId(), InvitationStatus.PENDING));
    }

    private OrganisationResponse toResponse(Organisation organisation, long coachCount, long pendingCount) {
        return mapper.toResponse(organisation, coachCount, pendingCount);
    }

    private static Map<UUID, Long> toMap(List<OrganisationCount> counts) {
        return counts.stream().collect(Collectors.toMap(OrganisationCount::organisationId, OrganisationCount::total));
    }

    private static void assertSortable(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidParameterException("sort");
            }
        }
    }

    /** Les listes à tri fixe ignorent le paramètre {@code sort} du client. */
    private static Pageable unsorted(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    /**
     * Les contraintes uniques protègent en dernier recours (requêtes concurrentes) : leur violation
     * est traduite en la même erreur métier que la vérification préalable du service.
     */
    private static <T> T saveAndFlush(Supplier<T> save) {
        try {
            return save.get();
        } catch (DataIntegrityViolationException ex) {
            if (violates(ex, UK_ORGANISATION_NAME)) {
                throw OrganisationErrors.nameTaken();
            }
            if (violates(ex, UK_ORGANISATION_COACH)) {
                throw OrganisationErrors.coachAlreadyMember();
            }
            if (violates(ex, UK_INVITATION_PENDING)) {
                throw OrganisationErrors.invitationAlreadyPending();
            }
            throw ex;
        }
    }

    private static boolean violates(DataIntegrityViolationException ex, String constraintName) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(constraintName);
    }
}
