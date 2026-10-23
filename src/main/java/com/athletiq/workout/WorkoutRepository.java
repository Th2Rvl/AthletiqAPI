package com.athletiq.workout;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkoutRepository extends JpaRepository<Workout, UUID> {

    /** Unicité du nom par coach, comparée avec la collation ai_ci (sans tenir compte de la casse ni des accents). */
    boolean existsByCoachIdAndName(UUID coachId, String name);

    boolean existsByCoachIdAndNameAndIdNot(UUID coachId, String name, UUID id);

    boolean existsByIdAndCoachId(UUID id, UUID coachId);

    /** Données de démonstration (profil dev) : la séance de ce nom, si elle existe déjà. */
    Optional<Workout> findByCoachIdAndName(UUID coachId, String name);

    /**
     * Détail, première des deux requêtes : la séance (filtrée par coach), ses exercices de séance et
     * l'exercice de bibliothèque de chacun. Les séries viennent de
     * {@link WorkoutExerciseRepository#fetchSetsOfWorkout}, pour éviter le produit cartésien
     * (et la {@code MultipleBagFetchException}) d'un double {@code join fetch}.
     */
    @Query("""
            select distinct w from Workout w
            left join fetch w.exercises we
            left join fetch we.exercise
            where w.id = :id and w.coach.id = :coachId
            """)
    Optional<Workout> findDetailByIdAndCoachId(@Param("id") UUID id, @Param("coachId") UUID coachId);

    /**
     * Séances d'un coach (RM1 : filtrées en base), de la plus récemment modifiée à la plus ancienne.
     * {@code pattern} est un motif LIKE déjà échappé (caractère {@code !}) ou {@code null}.
     */
    @Query(value = """
            select w from Workout w
            where w.coach.id = :coachId
              and (:pattern is null or w.name like :pattern escape '!')
            order by w.updatedAt desc, w.id desc
            """,
            countQuery = """
            select count(w) from Workout w
            where w.coach.id = :coachId
              and (:pattern is null or w.name like :pattern escape '!')
            """)
    Page<Workout> search(@Param("coachId") UUID coachId, @Param("pattern") String pattern, Pageable pageable);

    /**
     * Suppression en une requête : la base supprime les exercices de séance et les séries en cascade
     * ({@code ON DELETE CASCADE}), sans que Hibernate ait à les charger un par un.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Workout w where w.id = :id and w.coach.id = :coachId")
    int deleteByIdAndCoachId(@Param("id") UUID id, @Param("coachId") UUID coachId);
}
