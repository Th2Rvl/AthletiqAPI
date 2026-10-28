package com.athletiq.performance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Lectures de l'athlète sur son propre suivi (#11). Contrairement à {@link SessionPerformanceRepository}, qui
 * filtre par coach, chaque requête filtre ici par {@code ps.athlete.id} : l'athlète voit ses séances de tous ses
 * coachs, y compris ceux qu'il a quittés. Même définition d'une série « réalisée » et d'une séance réalisée :
 * une réalisation {@code VALIDATED}, jamais un brouillon.
 */
public interface AthleteTrackingRepository extends Repository<SessionPerformance, UUID> {

    /** Historique : séances réalisées ({@code includeDone}) et/ou non faites ({@code includeMissed}). */
    @Query(value = """
            select new com.athletiq.performance.SessionHistoryRow(ps.id, ps.scheduledDate, s.name, sp.id,
                   sp.rpe, sp.fatigue, case when sp.comment is not null then true else false end,
                   sp.validatedAt, sp.correctedAt)
            from PlannedSession ps
            join ps.snapshot s
            left join SessionPerformance sp on sp.plannedSession = ps
                 and sp.status = com.athletiq.performance.PerformanceStatus.VALIDATED
            where ps.athlete.id = :athleteId
              and ps.scheduledDate between :from and :to
              and ((:includeDone = true and sp.id is not null)
                   or (:includeMissed = true and sp.id is null and ps.scheduledDate < :today))
              and (:withComment = false or sp.comment is not null)
            order by ps.scheduledDate desc, ps.createdAt desc, ps.id desc
            """,
            countQuery = """
            select count(ps) from PlannedSession ps
            left join SessionPerformance sp on sp.plannedSession = ps
                 and sp.status = com.athletiq.performance.PerformanceStatus.VALIDATED
            where ps.athlete.id = :athleteId
              and ps.scheduledDate between :from and :to
              and ((:includeDone = true and sp.id is not null)
                   or (:includeMissed = true and sp.id is null and ps.scheduledDate < :today))
              and (:withComment = false or sp.comment is not null)
            """)
    Page<SessionHistoryRow> findHistory(@Param("athleteId") UUID athleteId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to,
                                        @Param("today") LocalDate today,
                                        @Param("includeDone") boolean includeDone,
                                        @Param("includeMissed") boolean includeMissed,
                                        @Param("withComment") boolean withComment,
                                        Pageable pageable);

    /** Séries réalisées de l'athlète, toutes périodes, regroupées par exercice, séance et charge (records, exercices). */
    @Query("""
            select new com.athletiq.performance.PerformedSetGroupRow(e.exerciseId, e.exerciseName, e.muscleGroup,
                   ps.id, ps.scheduledDate, ps.createdAt, pf.loadKg, max(pf.reps))
            from PerformedSet pf
            join pf.performance sp
            join sp.plannedSession ps
            join pf.snapshotSet st
            join st.snapshotExercise e
            where ps.athlete.id = :athleteId
              and sp.status = com.athletiq.performance.PerformanceStatus.VALIDATED
              and pf.reps is not null
            group by e.exerciseId, e.exerciseName, e.muscleGroup, ps.id, ps.scheduledDate, ps.createdAt, pf.loadKg
            """)
    List<PerformedSetGroupRow> findPerformedSetGroups(@Param("athleteId") UUID athleteId);

    /** Un point par séance réalisée de l'exercice à partir de {@code from}, sans borne haute (Q17). */
    @Query("""
            select new com.athletiq.performance.ProgressionRow(ps.id, ps.scheduledDate, ps.createdAt,
                   max(pf.loadKg), max(pf.reps), sum(pf.reps * pf.loadKg))
            from PerformedSet pf
            join pf.performance sp
            join sp.plannedSession ps
            join pf.snapshotSet st
            join st.snapshotExercise e
            where ps.athlete.id = :athleteId and e.exerciseId = :exerciseId
              and sp.status = com.athletiq.performance.PerformanceStatus.VALIDATED
              and pf.reps is not null
              and ps.scheduledDate >= :from
            group by ps.id, ps.scheduledDate, ps.createdAt
            order by ps.scheduledDate asc, ps.createdAt asc, ps.id asc
            """)
    List<ProgressionRow> findProgressionRows(@Param("athleteId") UUID athleteId,
                                             @Param("exerciseId") UUID exerciseId, @Param("from") LocalDate from);

    /** Libellé de l'exercice dans sa séance réalisée la plus récente ; vide si l'athlète ne l'a jamais réalisé. */
    @Query("""
            select new com.athletiq.performance.ExerciseLabel(e.exerciseName, e.muscleGroup)
            from PerformedSet pf
            join pf.performance sp
            join sp.plannedSession ps
            join pf.snapshotSet st
            join st.snapshotExercise e
            where ps.athlete.id = :athleteId and e.exerciseId = :exerciseId
              and sp.status = com.athletiq.performance.PerformanceStatus.VALIDATED
              and pf.reps is not null
            order by ps.scheduledDate desc, ps.createdAt desc, ps.id desc
            """)
    List<ExerciseLabel> findLatestLabels(@Param("athleteId") UUID athleteId,
                                         @Param("exerciseId") UUID exerciseId, Pageable pageable);

    /**
     * Assiduité : séances passées de l'athlète du {@code from} au {@code to} inclus, réalisées et non faites, non
     * faites depuis {@code recentFrom}, somme et nombre des RPE des réalisées. Aucune ligne sans séance passée.
     */
    @Query("""
            select new com.athletiq.performance.AthleteKpiRow(ps.athlete.id,
                   sum(case when sp.id is not null then 1 else 0 end),
                   sum(case when sp.id is null then 1 else 0 end),
                   sum(case when sp.id is null and ps.scheduledDate >= :recentFrom then 1 else 0 end),
                   sum(sp.rpe), count(sp.rpe))
            from PlannedSession ps
            left join SessionPerformance sp on sp.plannedSession = ps
                 and sp.status = com.athletiq.performance.PerformanceStatus.VALIDATED
            where ps.athlete.id = :athleteId and ps.scheduledDate between :from and :to
            group by ps.athlete.id
            """)
    Optional<AthleteKpiRow> aggregateKpi(@Param("athleteId") UUID athleteId, @Param("from") LocalDate from,
                                         @Param("to") LocalDate to, @Param("recentFrom") LocalDate recentFrom);
}
