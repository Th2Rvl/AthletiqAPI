package com.athletiq.common.security;

import com.athletiq.athlete.AthleteErrors;
import com.athletiq.athlete.AthleteLinkService;
import com.athletiq.common.exception.ErrorCodes;
import com.athletiq.common.exception.ForbiddenActionException;
import com.athletiq.group.GroupErrors;
import com.athletiq.group.GroupOwnershipService;
import com.athletiq.user.Role;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Contrôles d'accès appelés par les services, avant toute lecture ou modification.
 * C'est la dernière ligne de défense, après la {@code SecurityFilterChain} et {@code @PreAuthorize}.
 *
 * <p>Deux familles de refus :
 * <ul>
 *   <li>rôle manquant : 403 {@code ACCESS_DENIED} ;</li>
 *   <li>ressource qui n'appartient pas au coach courant : 404, comme si elle n'existait pas (RM4),
 *       pour ne pas révéler son existence.</li>
 * </ul>
 * Les questions de propriété passent par les services des fonctionnalités, jamais par leurs
 * repositories.
 */
@Component
@RequiredArgsConstructor
public class AccessGuard {

    private final CurrentUser currentUser;
    private final AthleteLinkService athleteLinkService;
    private final GroupOwnershipService groupOwnershipService;

    /** L'utilisateur courant doit être administrateur de la plateforme. */
    public void assertAdmin() {
        assertRole(Role.ADMIN);
    }

    /** L'utilisateur courant doit être un coach. */
    public void assertCoach() {
        assertRole(Role.COACH);
    }

    /** L'utilisateur courant doit être un athlète. */
    public void assertAthlete() {
        assertRole(Role.ATHLETE);
    }

    /**
     * L'utilisateur courant doit agir sur son propre compte (RM4). Un compte ne peut modifier que
     * son profil : l'identité vient du JWT, jamais d'un paramètre envoyé par le client.
     */
    public void assertSelf(UUID userId) {
        if (!currentUser.id().equals(userId)) {
            throw new ForbiddenActionException(ErrorCodes.ACCESS_DENIED, "Accès refusé");
        }
    }

    /** Le coach courant doit suivre activement cet athlète, sinon 404 {@code ATHLETE_NOT_FOUND}. */
    public void assertCoachOfAthlete(UUID athleteId) {
        assertCoach();
        if (!athleteLinkService.isFollowedBy(currentUser.id(), athleteId)) {
            throw AthleteErrors.athleteNotFound();
        }
    }

    /**
     * Le coach courant doit suivre activement chacun de ces athlètes (une requête pour tout le lot),
     * sinon 404 {@code ATHLETE_NOT_FOUND} pour le lot entier.
     */
    public void assertCoachOfAthletes(Collection<UUID> athleteIds) {
        assertCoach();
        if (!athleteLinkService.followsAll(currentUser.id(), athleteIds)) {
            throw AthleteErrors.athleteNotFound();
        }
    }

    /** Le groupe doit appartenir au coach courant, sinon 404 {@code GROUP_NOT_FOUND}. */
    public void assertGroupOwner(UUID groupId) {
        assertCoach();
        if (!groupOwnershipService.isOwnedBy(groupId, currentUser.id())) {
            throw GroupErrors.groupNotFound();
        }
    }

    /**
     * L'invitation doit appartenir au coach courant et être encore en attente, sinon 404
     * {@code INVITATION_NOT_FOUND}.
     */
    public void assertInvitationOwner(UUID invitationId) {
        assertCoach();
        if (!athleteLinkService.isPendingInvitationOf(currentUser.id(), invitationId)) {
            throw AthleteErrors.invitationNotFound();
        }
    }

    private void assertRole(Role role) {
        if (currentUser.role().filter(role::equals).isEmpty()) {
            throw new ForbiddenActionException(ErrorCodes.ACCESS_DENIED, "Accès refusé");
        }
    }
}
