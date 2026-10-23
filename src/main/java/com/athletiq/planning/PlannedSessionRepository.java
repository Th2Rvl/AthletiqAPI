package com.athletiq.planning;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Toutes les requêtes filtrent par {@code coach_id} (RM1) : jamais un {@code findAll()} filtré après coup. */
public interface PlannedSessionRepository extends JpaRepository<PlannedSession, UUID> {

    /** Athlète d'une séance planifiée de ce coach, pour la question de propriété (lien actif vérifié ensuite). */
    @Query("select ps.athlete.id from PlannedSession ps where ps.id = :id and ps.coach.id = :coachId")
    Optional<UUID> findAthleteIdByIdAndCoachId(@Param("id") UUID id, @Param("coachId") UUID coachId);

    @Query("""
            select ps from PlannedSession ps
            join fetch ps.snapshot
            where ps.id = :id and ps.coach.id = :coachId
            """)
    Optional<PlannedSession> findByIdAndCoachId(@Param("id") UUID id, @Param("coachId") UUID coachId);

    /** Calendrier d'un athlète : séances de ce coach seulement, triées par date, création, id. */
    @Query("""
            select new com.athletiq.planning.PlannedSessionRow(ps.id, ps.athlete.id, ps.scheduledDate, s.id, s.name,
                   ps.groupId, ps.sourceWorkoutId, ps.createdAt)
            from PlannedSession ps
            join ps.snapshot s
            where ps.coach.id = :coachId and ps.athlete.id = :athleteId
              and ps.scheduledDate between :from and :to
            order by ps.scheduledDate asc, ps.createdAt asc, ps.id asc
            """)
    List<PlannedSessionRow> findAthleteRows(@Param("coachId") UUID coachId, @Param("athleteId") UUID athleteId,
                                            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Calendrier d'un groupe : séances de ce coach planifiées via ce groupe, pour ces athlètes (membres actuels). */
    @Query("""
            select new com.athletiq.planning.PlannedSessionRow(ps.id, ps.athlete.id, ps.scheduledDate, s.id, s.name,
                   ps.groupId, ps.sourceWorkoutId, ps.createdAt)
            from PlannedSession ps
            join ps.snapshot s
            where ps.coach.id = :coachId and ps.groupId = :groupId and ps.athlete.id in :athleteIds
              and ps.scheduledDate between :from and :to
            order by ps.scheduledDate asc, ps.createdAt asc, ps.id asc
            """)
    List<PlannedSessionRow> findGroupRows(@Param("coachId") UUID coachId, @Param("groupId") UUID groupId,
                                          @Param("athleteIds") Collection<UUID> athleteIds,
                                          @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Sources d'une duplication de groupe : séances de ce coach planifiées via ce groupe, tout athlète confondu. */
    @Query("""
            select new com.athletiq.planning.PlannedSessionRow(ps.id, ps.athlete.id, ps.scheduledDate, s.id, s.name,
                   ps.groupId, ps.sourceWorkoutId, ps.createdAt)
            from PlannedSession ps
            join ps.snapshot s
            where ps.coach.id = :coachId and ps.groupId = :groupId
              and ps.scheduledDate between :from and :to
            order by ps.scheduledDate asc, ps.createdAt asc, ps.id asc
            """)
    List<PlannedSessionRow> findGroupSourceRows(@Param("coachId") UUID coachId, @Param("groupId") UUID groupId,
                                                @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Données de démonstration (profil dev) : ce coach a-t-il déjà planifié quelque chose ? */
    boolean existsByCoachId(UUID coachId);

    /** Séances de ce coach pour cet athlète à partir de ce jour inclus (confirmation de retrait, P7). */
    long countByCoachIdAndAthleteIdAndScheduledDateGreaterThanEqual(UUID coachId, UUID athleteId, LocalDate from);

    @Query("""
            select distinct ps.snapshot.id from PlannedSession ps
            where ps.coach.id = :coachId and ps.athlete.id = :athleteId and ps.scheduledDate >= :from
            """)
    List<UUID> findUpcomingSnapshotIds(@Param("coachId") UUID coachId, @Param("athleteId") UUID athleteId,
                                       @Param("from") LocalDate from);

    /** P7 : supprime les séances de ce coach pour cet athlète à partir de ce jour inclus ; passé et autres coachs intacts. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from PlannedSession ps
            where ps.coach.id = :coachId and ps.athlete.id = :athleteId and ps.scheduledDate >= :from
            """)
    int deleteUpcoming(@Param("coachId") UUID coachId, @Param("athleteId") UUID athleteId,
                       @Param("from") LocalDate from);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PlannedSession ps where ps.id = :id and ps.coach.id = :coachId")
    int deleteByIdAndCoachId(@Param("id") UUID id, @Param("coachId") UUID coachId);

    /** Q11 : séances planifiées distinctes (passées comprises) dont l'instantané contient cet exercice. */
    @Query("""
            select count(distinct ps.id) from PlannedSession ps
            join ps.snapshot s
            join s.exercises e
            where e.exerciseId = :exerciseId
            """)
    long countUsingExercise(@Param("exerciseId") UUID exerciseId);
}
