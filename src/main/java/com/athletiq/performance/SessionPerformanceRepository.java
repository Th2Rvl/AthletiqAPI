package com.athletiq.performance;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Lectures du réalisé (RM1) : des requêtes groupées qui joignent la séance planifiée et son instantané en lecture
 * seule, toujours filtrées par {@code ps.coach.id = :coachId} (séances que ce coach a planifiées), y compris
 * dans les agrégats ; une requête par écran, jamais une par athlète ou par séance. Seule une réalisation
 * {@code VALIDATED} est lue (RM3) : la jointure la filtre dans sa condition, si bien qu'une séance au brouillon
 * se lit comme une séance sans réalisation.
 */
public interface SessionPerformanceRepository extends JpaRepository<SessionPerformance, UUID> {

    /** Port {@code planning.PlannedSessionCompletion} : séances réalisées parmi celles-ci. */
    @Query("""
            select sp.plannedSession.id from SessionPerformance sp
            where sp.plannedSession.id in :plannedSessionIds
              and sp.status = com.athletiq.performance.PerformanceStatus.VALIDATED
            """)
    List<UUID> findValidatedPlannedSessionIds(@Param("plannedSessionIds") Collection<UUID> plannedSessionIds);

    /** Données de démonstration (profil dev) : ce coach a-t-il déjà des réalisations sur ses séances ? */
    boolean existsByPlannedSessionCoachId(UUID coachId);

    /**
     * En-tête du suivi : nombre de séances de ce coach pour cet athlète qui entrent dans l'historique, c'est-à-dire
     * réalisées (quelle que soit leur date) ou passées.
     */
    @Query("""
            select count(ps) from PlannedSession ps
            where ps.coach.id = :coachId and ps.athlete.id = :athleteId
              and (ps.scheduledDate < :today
                   or exists (select sp.id from SessionPerformance sp where sp.plannedSession = ps
                              and sp.status = com.athletiq.performance.PerformanceStatus.VALIDATED))
            """)
    long countPastOrDone(@Param("coachId") UUID coachId, @Param("athleteId") UUID athleteId,
                         @Param("today") LocalDate today);

    /**
     * US27, US29 : séances de ce coach pour cet athlète du {@code from} au {@code to} inclus, réalisées
     * ({@code includeDone}) et/ou non faites, c'est-à-dire passées sans réalisation validée
     * ({@code includeMissed}) ; {@code withComment} ne garde que les réalisées commentées. Tri fixe : date,
     * création puis id, du plus récent au plus ancien.
     */
    @Query(value = """
            select new com.athletiq.performance.SessionHistoryRow(ps.id, ps.scheduledDate, s.name, sp.id,
                   sp.rpe, sp.fatigue, case when sp.comment is not null then true else false end,
                   sp.validatedAt, sp.correctedAt)
            from PlannedSession ps
            join ps.snapshot s
            left join SessionPerformance sp on sp.plannedSession = ps
                 and sp.status = com.athletiq.performance.PerformanceStatus.VALIDATED
            where ps.coach.id = :coachId and ps.athlete.id = :athleteId
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
            where ps.coach.id = :coachId and ps.athlete.id = :athleteId
              and ps.scheduledDate between :from and :to
              and ((:includeDone = true and sp.id is not null)
                   or (:includeMissed = true and sp.id is null and ps.scheduledDate < :today))
              and (:withComment = false or sp.comment is not null)
            """)
    Page<SessionHistoryRow> findHistory(@Param("coachId") UUID coachId,
                                        @Param("athleteId") UUID athleteId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to,
                                        @Param("today") LocalDate today,
                                        @Param("includeDone") boolean includeDone,
                                        @Param("includeMissed") boolean includeMissed,
                                        @Param("withComment") boolean withComment,
                                        Pageable pageable);

    /** US28, US29 : la séance planifiée de ce coach, son instantané et le ressenti de sa réalisation validée. */
    @Query("""
            select new com.athletiq.performance.ReviewHeaderRow(ps.id, ps.athlete.id, ps.scheduledDate, s.name,
                   s.description, sp.id, sp.rpe, sp.fatigue, sp.comment, sp.validatedAt, sp.correctedAt)
            from PlannedSession ps
            join ps.snapshot s
            left join SessionPerformance sp on sp.plannedSession = ps
                 and sp.status = com.athletiq.performance.PerformanceStatus.VALIDATED
            where ps.id = :plannedSessionId and ps.coach.id = :coachId
            """)
    Optional<ReviewHeaderRow> findReviewHeader(@Param("coachId") UUID coachId,
                                               @Param("plannedSessionId") UUID plannedSessionId);

    /**
     * US28 : chaque série prévue de l'instantané (RM5 : jamais le modèle), avec en face la série réalisée de la
     * réalisation validée, triées par position d'exercice puis de série.
     */
    @Query("""
            select new com.athletiq.performance.ReviewLineRow(e.id, e.exerciseId, e.exerciseName, e.muscleGroup,
                   e.position, e.note, st.id, st.position, st.reps, st.loadKg, st.restSeconds, pf.reps, pf.loadKg)
            from PlannedSession ps
            join ps.snapshot s
            join s.exercises e
            left join e.sets st
            left join SessionPerformance sp on sp.plannedSession = ps
                 and sp.status = com.athletiq.performance.PerformanceStatus.VALIDATED
            left join PerformedSet pf on pf.performance = sp and pf.snapshotSet = st
            where ps.id = :plannedSessionId and ps.coach.id = :coachId
            order by e.position asc, st.position asc
            """)
    List<ReviewLineRow> findReviewLines(@Param("coachId") UUID coachId,
                                        @Param("plannedSessionId") UUID plannedSessionId);
}
