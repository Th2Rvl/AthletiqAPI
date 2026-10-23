package com.athletiq.common.dev;

import com.athletiq.athlete.CoachAthlete;
import com.athletiq.athlete.CoachAthleteRepository;
import com.athletiq.athlete.CoachInvitation;
import com.athletiq.athlete.CoachInvitationRepository;
import com.athletiq.athlete.InvitationCodeCipher;
import com.athletiq.athlete.InvitationProperties;
import com.athletiq.exercise.Exercise;
import com.athletiq.exercise.ExerciseRepository;
import com.athletiq.exercise.MuscleGroup;
import com.athletiq.group.AthleteGroup;
import com.athletiq.group.AthleteGroupMember;
import com.athletiq.group.AthleteGroupMemberRepository;
import com.athletiq.group.AthleteGroupRepository;
import com.athletiq.organisation.InvitationStatus;
import com.athletiq.organisation.Organisation;
import com.athletiq.organisation.OrganisationCoach;
import com.athletiq.organisation.OrganisationCoachRepository;
import com.athletiq.organisation.OrganisationInvitation;
import com.athletiq.organisation.OrganisationInvitationRepository;
import com.athletiq.organisation.OrganisationRepository;
import com.athletiq.planning.PlannedSession;
import com.athletiq.planning.PlannedSessionRepository;
import com.athletiq.planning.PlanningClock;
import com.athletiq.planning.SessionSnapshot;
import com.athletiq.planning.SessionSnapshotRepository;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserService;
import com.athletiq.workout.PlannedSet;
import com.athletiq.workout.Workout;
import com.athletiq.workout.WorkoutExercise;
import com.athletiq.workout.WorkoutMapper;
import com.athletiq.workout.WorkoutRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Données de démonstration, uniquement en profil {@code dev}. Idempotent : un compte est ignoré si son
 * e-mail existe déjà, une organisation si son nom existe déjà. Le mot de passe des comptes de démo
 * vient de {@code ATHLETIQ_DEV_DEMO_PASSWORD}, sans valeur par défaut : s'il est absent, rien n'est créé.
 * Ainsi, une application démarrée par erreur avec le profil dev par défaut n'ouvre aucun compte connu.
 *
 * <p>Suivi des athlètes (#3) : le coach de démo {@code coach@club.fr} suit six athlètes répartis en
 * deux groupes, Lucas Girard est aussi suivi par Claire Martin (plusieurs coachs, RM2), et une
 * invitation en attente porte le code connu {@value #DEMO_INVITATION_CODE} (dev uniquement).
 *
 * <p>Planification (#6) : le coach de démo a une petite bibliothèque (exercices, deux séances) et, s'il n'a
 * encore rien planifié, une séance individuelle à venir pour Lucas, une seconde le même jour (plusieurs
 * séances par jour, Q1), une séance passée (verrouillée), et une planification du groupe « Sprint »
 * (instantané partagé par ses membres).
 *
 * <p>Les organisations sont écrites par les repositories et non par {@code OrganisationService} :
 * au démarrage il n'y a pas d'utilisateur connecté, donc pas d'admin pour passer {@code assertAdmin()}.
 * Ce composant d'infrastructure n'est jamais chargé hors du profil dev.
 */
@Slf4j
@Component
@Profile("dev")
@Order(2)
public class DevDataSeeder implements ApplicationRunner {

    private static final String RENNES = "Stade Rennais Athlétisme";
    private static final String NANTES = "Nantes Métropole Athlétisme";
    private static final String MORBIHAN = "Trail Club du Morbihan";

    private record DemoUser(String email, String firstName, String lastName, Role role, List<String> organisations) {
    }

    private static final List<DemoUser> USERS = List.of(
            new DemoUser("claire.martin@stade-rennais-athle.fr", "Claire", "Martin", Role.COACH, List.of(RENNES, NANTES)),
            new DemoUser("julien.lefebvre@stade-rennais-athle.fr", "Julien", "Lefebvre", Role.COACH, List.of(RENNES)),
            new DemoUser("sophie.bernard@nantes-metropole-athle.fr", "Sophie", "Bernard", Role.COACH, List.of(NANTES)),
            new DemoUser("camille.dubois@nantes-metropole-athle.fr", "Camille", "Dubois", Role.COACH, List.of(NANTES)),
            new DemoUser("thomas.moreau@coach-independant.fr", "Thomas", "Moreau", Role.COACH, List.of()),
            new DemoUser("coach@club.fr", "Nicolas", "Petit", Role.COACH, List.of()),
            new DemoUser("lucas.girard@athlete.fr", "Lucas", "Girard", Role.ATHLETE, List.of()),
            new DemoUser("emma.lefevre@athlete.fr", "Emma", "Lefèvre", Role.ATHLETE, List.of()),
            new DemoUser("hugo.fontaine@athlete.fr", "Hugo", "Fontaine", Role.ATHLETE, List.of()),
            new DemoUser("lea.garnier@athlete.fr", "Léa", "Garnier", Role.ATHLETE, List.of()),
            new DemoUser("maelys.rousseau@athlete.fr", "Maëlys", "Rousseau", Role.ATHLETE, List.of()),
            new DemoUser("nathan.chevalier@athlete.fr", "Nathan", "Chevalier", Role.ATHLETE, List.of()));

    /** Code d'invitation de démonstration, connu pour tester la saisie depuis l'app mobile. Dev uniquement. */
    static final String DEMO_INVITATION_CODE = "K7QH3MPA";
    private static final String DEMO_COACH = "coach@club.fr";

    private record DemoLink(String coachEmail, String athleteEmail, int joinedDaysAgo) {
    }

    private static final List<DemoLink> LINKS = List.of(
            new DemoLink(DEMO_COACH, "lucas.girard@athlete.fr", 120),
            new DemoLink(DEMO_COACH, "emma.lefevre@athlete.fr", 95),
            new DemoLink(DEMO_COACH, "hugo.fontaine@athlete.fr", 60),
            new DemoLink(DEMO_COACH, "lea.garnier@athlete.fr", 41),
            new DemoLink(DEMO_COACH, "maelys.rousseau@athlete.fr", 18),
            new DemoLink(DEMO_COACH, "nathan.chevalier@athlete.fr", 3),
            new DemoLink("claire.martin@stade-rennais-athle.fr", "lucas.girard@athlete.fr", 200));

    private static final Map<String, List<String>> GROUPS = Map.of(
            "Sprint", List.of("lucas.girard@athlete.fr", "emma.lefevre@athlete.fr", "hugo.fontaine@athlete.fr"),
            "Demi-fond", List.of("lea.garnier@athlete.fr", "maelys.rousseau@athlete.fr", "emma.lefevre@athlete.fr"));

    private final UserService userService;
    private final OrganisationRepository organisationRepository;
    private final OrganisationCoachRepository coachRepository;
    private final OrganisationInvitationRepository invitationRepository;
    private final CoachAthleteRepository linkRepository;
    private final CoachInvitationRepository coachInvitationRepository;
    private final AthleteGroupRepository groupRepository;
    private final AthleteGroupMemberRepository groupMemberRepository;
    private final InvitationCodeCipher invitationCodeCipher;
    private final InvitationProperties invitationProperties;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutRepository workoutRepository;
    private final WorkoutMapper workoutMapper;
    private final PlannedSessionRepository plannedSessionRepository;
    private final SessionSnapshotRepository snapshotRepository;
    private final PlanningClock planningClock;
    private final Clock clock;
    private final String demoPassword;

    public DevDataSeeder(UserService userService,
                         OrganisationRepository organisationRepository,
                         OrganisationCoachRepository coachRepository,
                         OrganisationInvitationRepository invitationRepository,
                         CoachAthleteRepository linkRepository,
                         CoachInvitationRepository coachInvitationRepository,
                         AthleteGroupRepository groupRepository,
                         AthleteGroupMemberRepository groupMemberRepository,
                         InvitationCodeCipher invitationCodeCipher,
                         InvitationProperties invitationProperties,
                         ExerciseRepository exerciseRepository,
                         WorkoutRepository workoutRepository,
                         WorkoutMapper workoutMapper,
                         PlannedSessionRepository plannedSessionRepository,
                         SessionSnapshotRepository snapshotRepository,
                         PlanningClock planningClock,
                         Clock clock,
                         @Value("${athletiq.dev.demo-password:}") String demoPassword) {
        this.userService = userService;
        this.organisationRepository = organisationRepository;
        this.coachRepository = coachRepository;
        this.invitationRepository = invitationRepository;
        this.linkRepository = linkRepository;
        this.coachInvitationRepository = coachInvitationRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.invitationCodeCipher = invitationCodeCipher;
        this.invitationProperties = invitationProperties;
        this.exerciseRepository = exerciseRepository;
        this.workoutRepository = workoutRepository;
        this.workoutMapper = workoutMapper;
        this.plannedSessionRepository = plannedSessionRepository;
        this.snapshotRepository = snapshotRepository;
        this.planningClock = planningClock;
        this.clock = clock;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (demoPassword == null || demoPassword.isBlank()) {
            log.warn("ATHLETIQ_DEV_DEMO_PASSWORD absent : aucune donnée de démonstration n'est créée");
            return;
        }
        Map<String, Organisation> created = new HashMap<>();
        createOrganisation(RENNES, "Club d'athlétisme rennais : sprint, demi-fond et épreuves combinées.", created);
        createOrganisation(NANTES, "Section piste et cross de la métropole nantaise.", created);
        createOrganisation(MORBIHAN, null, created);

        for (DemoUser demo : USERS) {
            if (userService.existsByEmail(demo.email())) {
                continue;
            }
            User user = userService.create(demo.email(), demoPassword, demo.firstName(), demo.lastName(), demo.role());
            for (String organisationName : demo.organisations()) {
                Organisation organisation = created.get(organisationName);
                if (organisation != null) {
                    coachRepository.save(OrganisationCoach.builder()
                            .organisation(organisation)
                            .coach(user)
                            .joinedAt(clock.instant())
                            .build());
                }
            }
        }

        Organisation rennes = created.get(RENNES);
        if (rennes != null) {
            invitationRepository.save(OrganisationInvitation.builder()
                    .organisation(rennes)
                    .email("manon.roux@stade-rennais-athle.fr")
                    .status(InvitationStatus.PENDING)
                    .build());
        }
        log.info("Données de démonstration : {} organisation(s) créée(s)", created.size());
        seedCoachAthletes();
    }

    /** Liens, groupes et invitation de démonstration ; chaque élément n'est créé que s'il manque. */
    private void seedCoachAthletes() {
        Map<String, User> users = new HashMap<>();
        for (DemoUser demo : USERS) {
            userService.findByEmail(demo.email()).ifPresent(user -> users.put(demo.email(), user));
        }
        Instant now = clock.instant();
        for (DemoLink demo : LINKS) {
            User coach = users.get(demo.coachEmail());
            User athlete = users.get(demo.athleteEmail());
            if (coach != null && athlete != null
                    && !linkRepository.existsByCoachIdAndAthleteIdAndEndedAtIsNull(coach.getId(), athlete.getId())) {
                linkRepository.save(CoachAthlete.builder()
                        .coach(coach)
                        .athlete(athlete)
                        .joinedAt(now.minus(Duration.ofDays(demo.joinedDaysAgo())))
                        .build());
            }
        }

        User coach = users.get(DEMO_COACH);
        if (coach == null) {
            return;
        }
        GROUPS.forEach((name, members) -> {
            AthleteGroup group = groupRepository.findByCoachIdAndName(coach.getId(), name)
                    .orElseGet(() -> groupRepository.save(AthleteGroup.builder().coach(coach).name(name).build()));
            for (String email : members) {
                User athlete = users.get(email);
                if (athlete != null && groupMemberRepository
                        .findAthleteIdsByGroupIdAndAthleteIdIn(group.getId(), List.of(athlete.getId())).isEmpty()) {
                    groupMemberRepository.save(AthleteGroupMember.builder()
                            .group(group)
                            .athlete(athlete)
                            .addedAt(now)
                            .build());
                }
            }
        });

        // Le code de démo est prolongé à chaque démarrage tant qu'il n'a été ni utilisé ni révoqué.
        String hash = invitationCodeCipher.hash(DEMO_INVITATION_CODE);
        coachInvitationRepository.findByCodeHash(hash).ifPresentOrElse(
                invitation -> {
                    if (invitation.getConsumedAt() == null && invitation.getRevokedAt() == null) {
                        invitation.setExpiresAt(now.plus(invitationProperties.ttl()));
                    }
                },
                () -> coachInvitationRepository.save(CoachInvitation.builder()
                        .coach(coach)
                        .label("Inès Lambert")
                        .codeEncrypted(invitationCodeCipher.encrypt(DEMO_INVITATION_CODE, coach.getId()))
                        .codeHash(hash)
                        .expiresAt(now.plus(invitationProperties.ttl()))
                        .build()));

        seedPlanning(coach, users);
    }

    /** Bibliothèque et planning de démonstration du coach de démo ; le planning n'est créé qu'une fois. */
    private void seedPlanning(User coach, Map<String, User> users) {
        Exercise bench = exercise(coach, "Développé couché", MuscleGroup.PECTORAUX, "Barre");
        Exercise row = exercise(coach, "Rowing barre", MuscleGroup.DOS, "Barre");
        Exercise squat = exercise(coach, "Squat", MuscleGroup.QUADRICEPS, "Barre");
        Exercise plank = exercise(coach, "Gainage", MuscleGroup.ABDOMINAUX, null);
        Workout upper = workout(coach, "Haut du corps A", "Échauffement 10 min, puis travail en force.",
                List.of(exercise(bench, "Tempo 3-1-1", set(8, "60.0", 90), set(8, "60.0", 90), set(6, "65.0", 120)),
                        exercise(row, null, set(10, "50.0", 90), set(10, "50.0", 90))));
        Workout lower = workout(coach, "Bas du corps A", null,
                List.of(exercise(squat, null, set(5, "80.0", 150), set(5, "80.0", 150), set(5, "80.0", 150)),
                        exercise(plank, "45 s par série", set(1, null, 60), set(1, null, 60))));

        if (plannedSessionRepository.existsByCoachId(coach.getId())) {
            return;
        }
        LocalDate today = planningClock.today();
        User lucas = users.get("lucas.girard@athlete.fr");
        if (lucas != null) {
            plan(coach, lucas, today.plusDays(2), upper);
            plan(coach, lucas, today.plusDays(2), lower);
            plan(coach, lucas, today.minusDays(5), lower);
        }
        groupRepository.findByCoachIdAndName(coach.getId(), "Sprint").ifPresent(group -> {
            SessionSnapshot shared = snapshotRepository.save(
                    SessionSnapshot.copyOf(coach, workoutMapper.toResponse(upper)));
            GROUPS.get("Sprint").stream().map(users::get).filter(Objects::nonNull)
                    .forEach(athlete -> plannedSessionRepository.save(PlannedSession.builder()
                            .coach(coach)
                            .athlete(athlete)
                            .scheduledDate(today.plusDays(4))
                            .snapshot(shared)
                            .sourceWorkoutId(upper.getId())
                            .groupId(group.getId())
                            .build()));
        });
        log.info("Données de démonstration : planning du coach {} créé", DEMO_COACH);
    }

    private Exercise exercise(User coach, String name, MuscleGroup muscleGroup, String equipment) {
        return exerciseRepository.findByCoachIdAndName(coach.getId(), name)
                .orElseGet(() -> exerciseRepository.save(Exercise.builder()
                        .coach(coach)
                        .name(name)
                        .muscleGroup(muscleGroup)
                        .equipment(equipment)
                        .build()));
    }

    private Workout workout(User coach, String name, String description, List<WorkoutExercise> exercises) {
        return workoutRepository.findByCoachIdAndName(coach.getId(), name).orElseGet(() -> {
            Workout workout = Workout.builder().coach(coach).name(name).description(description).build();
            exercises.forEach(workout::addExercise);
            return workoutRepository.save(workout);
        });
    }

    private static WorkoutExercise exercise(Exercise exercise, String note, PlannedSet... sets) {
        WorkoutExercise workoutExercise = WorkoutExercise.builder().exercise(exercise).note(note).build();
        for (PlannedSet set : sets) {
            workoutExercise.addSet(set);
        }
        return workoutExercise;
    }

    private static PlannedSet set(int reps, String loadKg, Integer restSeconds) {
        return PlannedSet.builder()
                .reps(reps)
                .loadKg(loadKg == null ? null : new BigDecimal(loadKg))
                .restSeconds(restSeconds)
                .build();
    }

    /** Une planification individuelle : un instantané neuf, comme {@code PlanningService.planForAthlete}. */
    private void plan(User coach, User athlete, LocalDate date, Workout workout) {
        SessionSnapshot snapshot = snapshotRepository.save(
                SessionSnapshot.copyOf(coach, workoutMapper.toResponse(workout)));
        plannedSessionRepository.save(PlannedSession.builder()
                .coach(coach)
                .athlete(athlete)
                .scheduledDate(date)
                .snapshot(snapshot)
                .sourceWorkoutId(workout.getId())
                .build());
    }

    private void createOrganisation(String name, String description, Map<String, Organisation> created) {
        if (organisationRepository.existsByName(name)) {
            return;
        }
        created.put(name, organisationRepository.save(Organisation.builder()
                .name(name)
                .description(description)
                .build()));
    }
}
