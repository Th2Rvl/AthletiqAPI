package com.athletiq.exercise;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {

    /** Unicité du nom par coach, comparée avec la collation ai_ci (sans tenir compte de la casse ni des accents). */
    boolean existsByCoachIdAndName(UUID coachId, String name);

    boolean existsByCoachIdAndNameAndIdNot(UUID coachId, String name, UUID id);

    boolean existsByIdAndCoachId(UUID id, UUID coachId);

    long countByCoachIdAndIdIn(UUID coachId, Collection<UUID> ids);

    /** Exercices de ce coach parmi ces identifiants (ceux d'un autre coach sont ignorés). */
    List<Exercise> findAllByCoachIdAndIdIn(UUID coachId, Collection<UUID> ids);

    /** Filtré par coach en base (RM4) : un exercice d'un autre coach n'est jamais renvoyé. */
    Optional<Exercise> findByIdAndCoachId(UUID id, UUID coachId);

    /**
     * Exercices d'un coach, triés par nom. {@code pattern} est un motif LIKE déjà échappé (caractère
     * {@code !}) ou {@code null} ; {@code muscleGroup} et {@code equipment} sont des égalités optionnelles.
     */
    @Query(value = """
            select e from Exercise e
            where e.coach.id = :coachId
              and (:pattern is null or e.name like :pattern escape '!')
              and (:muscleGroup is null or e.muscleGroup = :muscleGroup)
              and (:equipment is null or e.equipment = :equipment)
            order by e.name asc, e.id asc
            """,
            countQuery = """
            select count(e) from Exercise e
            where e.coach.id = :coachId
              and (:pattern is null or e.name like :pattern escape '!')
              and (:muscleGroup is null or e.muscleGroup = :muscleGroup)
              and (:equipment is null or e.equipment = :equipment)
            """)
    Page<Exercise> search(@Param("coachId") UUID coachId,
                          @Param("pattern") String pattern,
                          @Param("muscleGroup") MuscleGroup muscleGroup,
                          @Param("equipment") String equipment,
                          Pageable pageable);
}
