package com.athletiq.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.athletiq.TestcontainersConfiguration;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Bibliothèque d'exercices sur une vraie base MySQL 8.4 (Testcontainers) : migration V5 validée par
 * Hibernate ({@code ddl-auto=validate}), unicité du nom par coach, liste fermée des groupes musculaires
 * et filtres de recherche en base.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ExerciseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserService userService;
    @Autowired
    private ExerciseRepository exerciseRepository;

    @BeforeEach
    void cleanExercises() {
        // Les séances d'abord : leur clé vers exercise est en RESTRICT.
        jdbc.update("delete from workout");
        jdbc.update("delete from exercise");
    }

    private User coach() {
        return userService.create("coach-" + UUID.randomUUID() + "@club.fr", "mot-de-passe-123", "Claire", "Martin",
                Role.COACH);
    }

    private Exercise save(User coach, String name, MuscleGroup muscleGroup, String equipment) {
        return exerciseRepository.saveAndFlush(Exercise.builder()
                .coach(coach)
                .name(name)
                .muscleGroup(muscleGroup)
                .equipment(equipment)
                .build());
    }

    @Test
    void appliesMigrationV5() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = 1 and version = '5'", Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void theNameIsUniquePerCoachIgnoringCaseAndAccentsButNotAcrossCoaches() {
        User coach = coach();
        User other = coach();
        save(coach, "Développé couché", MuscleGroup.PECTORAUX, "BARRE");

        for (String duplicate : List.of("Développé couché", "developpe couche", "DÉVELOPPÉ COUCHÉ")) {
            assertThatThrownBy(() -> save(coach, duplicate, MuscleGroup.PECTORAUX, null))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_exercise_coach_name");
        }
        assertThat(exerciseRepository.existsByCoachIdAndName(coach.getId(), "DEVELOPPE COUCHE")).isTrue();
        save(other, "Développé couché", MuscleGroup.PECTORAUX, "BARRE");
    }

    @Test
    void theDatabaseRefusesAMuscleGroupOutsideTheFixedList() {
        User coach = coach();
        byte[] coachId = jdbc.queryForObject("select id from app_user where email = ?", byte[].class, coach.getEmail());

        assertThatThrownBy(() -> jdbc.update(
                "insert into exercise (id, coach_id, name, muscle_group, created_at, updated_at) "
                        + "values (UUID_TO_BIN(UUID(), 1), ?, 'Squat', 'JAMBES', now(6), now(6))",
                coachId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void searchFiltersByNameMuscleGroupAndEquipmentWithinTheCoachOnly() {
        User coach = coach();
        User other = coach();
        save(coach, "Développé couché", MuscleGroup.PECTORAUX, "BARRE");
        save(coach, "Squat", MuscleGroup.QUADRICEPS, "BARRE");
        save(coach, "Squat gobelet", MuscleGroup.QUADRICEPS, "HALTERES");
        save(coach, "Tirage", MuscleGroup.DOS, "POULIE");
        save(other, "Squat", MuscleGroup.QUADRICEPS, "BARRE");
        PageRequest page = PageRequest.of(0, 20);

        assertThat(names(exerciseRepository.search(coach.getId(), ExerciseService.likePattern("SQUAT"), null, null,
                page))).containsExactly("Squat", "Squat gobelet");
        assertThat(names(exerciseRepository.search(coach.getId(), ExerciseService.likePattern("developpe"), null, null,
                page))).containsExactly("Développé couché");
        assertThat(names(exerciseRepository.search(coach.getId(), null, MuscleGroup.QUADRICEPS, null, page)))
                .containsExactly("Squat", "Squat gobelet");
        assertThat(names(exerciseRepository.search(coach.getId(), null, MuscleGroup.QUADRICEPS, "barre", page)))
                .containsExactly("Squat");
        assertThat(names(exerciseRepository.search(coach.getId(), null, null, null, page)))
                .containsExactly("Développé couché", "Squat", "Squat gobelet", "Tirage");
        assertThat(exerciseRepository.search(coach.getId(), null, null, null, PageRequest.of(1, 3))
                .getTotalElements()).isEqualTo(4);
    }

    @Test
    void anotherCoachCannotFindAnExercise() {
        User coach = coach();
        User other = coach();
        Exercise squat = save(coach, "Squat", MuscleGroup.QUADRICEPS, "BARRE");

        assertThat(exerciseRepository.findByIdAndCoachId(squat.getId(), coach.getId())).isPresent();
        assertThat(exerciseRepository.findByIdAndCoachId(squat.getId(), other.getId())).isEmpty();
        assertThat(exerciseRepository.existsByIdAndCoachId(squat.getId(), other.getId())).isFalse();
    }

    private static List<String> names(org.springframework.data.domain.Page<Exercise> page) {
        return page.getContent().stream().map(Exercise::getName).toList();
    }
}
