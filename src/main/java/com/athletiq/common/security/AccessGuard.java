package com.athletiq.common.security;

import com.athletiq.athlete.AthleteErrors;
import com.athletiq.athlete.AthleteLinkService;
import com.athletiq.common.exception.ErrorCodes;
import com.athletiq.common.exception.ForbiddenActionException;
import com.athletiq.exercise.ExerciseErrors;
import com.athletiq.exercise.ExerciseOwnershipService;
import com.athletiq.group.GroupErrors;
import com.athletiq.group.GroupOwnershipService;
import com.athletiq.planning.PlannedSessionOwnershipService;
import com.athletiq.planning.PlanningErrors;
import com.athletiq.user.Role;
import com.athletiq.workout.WorkoutErrors;
import com.athletiq.workout.WorkoutOwnershipService;
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
    private final ExerciseOwnershipService exerciseOwnershipService;
    private final WorkoutOwnershipService workoutOwnershipService;
    private final PlannedSessionOwnershipService plannedSessionOwnershipService;

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
     * L'utilisateur courant doit être un athlète suivi par au moins un coach (US06) : sans coach, 403
     * {@code ATHLETE_WITHOUT_COACH}. Le front l'applique aussi, mais c'est ici que la règle est garantie.
     */
    public void assertAthleteWithCoach() {
        assertAthlete();
        if (!athleteLinkService.hasActiveCoach(currentUser.id())) {
            throw AthleteErrors.athleteWithoutCoach();
        }
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

    /** L'exercice doit appartenir au coach courant, sinon 404 {@code EXERCISE_NOT_FOUND}. */
    public void assertExerciseOwner(UUID exerciseId) {
        assertCoach();
        if (!exerciseOwnershipService.isOwnedBy(exerciseId, currentUser.id())) {
            throw ExerciseErrors.exerciseNotFound();
        }
    }

    /**
     * Chacun de ces exercices doit appartenir au coach courant (une requête pour tout le lot, doublons
     * permis), sinon 404 {@code EXERCISE_NOT_FOUND} pour le lot entier, sans dire lequel.
     */
    public void assertExercisesOwner(Collection<UUID> exerciseIds) {
        assertCoach();
        if (!exerciseOwnershipService.ownsAll(currentUser.id(), exerciseIds)) {
            throw ExerciseErrors.exerciseNotFound();
        }
    }

    /** La séance doit appartenir au coach courant, sinon 404 {@code WORKOUT_NOT_FOUND}. */
    public void assertWorkoutOwner(UUID workoutId) {
        assertCoach();
        if (!workoutOwnershipService.isOwnedBy(workoutId, currentUser.id())) {
            throw WorkoutErrors.workoutNotFound();
        }
    }

    /**
     * La séance planifiée doit appartenir au coach courant, qui suit toujours son athlète (RM1, RM2), sinon
     * 404 {@code PLANNED_SESSION_NOT_FOUND} : une séance d'un athlète au lien clos devient invisible.
     */
    public void assertPlannedSessionOwner(UUID plannedSessionId) {
        assertCoach();
        if (!plannedSessionOwnershipService.isOwnedBy(plannedSessionId, currentUser.id())) {
            throw PlanningErrors.plannedSessionNotFound();
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
