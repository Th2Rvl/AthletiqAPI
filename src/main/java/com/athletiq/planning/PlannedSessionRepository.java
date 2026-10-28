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

    /** Calendrier de l'athlète connecté : ses propres séances, tous coachs confondus, triées par date, création, id. */
    @Query("""
            select new com.athletiq.planning.AthleteSessionRow(ps.id, ps.scheduledDate, s.id, s.name,
                   c.id, c.firstName, c.lastName, ps.createdAt)
            from PlannedSession ps
            join ps.snapshot s
            join ps.coach c
            where ps.athlete.id = :athleteId and ps.scheduledDate between :from and :to
            order by ps.scheduledDate asc, ps.createdAt asc, ps.id asc
            """)
    List<AthleteSessionRow> findOwnRows(@Param("athleteId") UUID athleteId,
                                        @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Une séance de l'athlète connecté, avec son instantané et son coach. */
    @Query("""
            select ps from PlannedSession ps
            join fetch ps.snapshot
            join fetch ps.coach
            where ps.id = :id and ps.athlete.id = :athleteId
            """)
    Optional<PlannedSession> findOwnById(@Param("id") UUID id, @Param("athleteId") UUID athleteId);

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

    /*
     * P7 (#3, RM10 de #7) : les trois requêtes suivantes partagent la même définition, « séances de ce coach pour
     * cet athlète à partir de ce jour inclus, sans réalisation validée ». Le « not exists » nomme l'entité de
     * performance dans le JPQL seulement : aucune dépendance de compilation de planning vers performance.
     */

    /** Séances que supprimerait le retrait de cet athlète (confirmation de retrait, même définition que P7). */
    @Query("""
            select count(ps) from PlannedSession ps
            where ps.coach.id = :coachId and ps.athlete.id = :athleteId and ps.scheduledDate >= :from
              and not exists (select sp.id from SessionPerformance sp where sp.plannedSession = ps
                              and sp.status = com.athletiq.performance.PerformanceStatus.VALIDATED)
            """)
    long countUpcoming(@Param("coachId") UUID coachId, @Param("athleteId") UUID athleteId,
                       @Param("from") LocalDate from);

    @Query("""
            select distinct ps.snapshot.id from PlannedSession ps
            where ps.coach.id = :coachId and ps.athlete.id = :athleteId and ps.scheduledDate >= :from
              and not exists (select sp.id from SessionPerformance sp where sp.plannedSession = ps
                              and sp.status = com.athletiq.performance.PerformanceStatus.VALIDATED)
            """)
    List<UUID> findUpcomingSnapshotIds(@Param("coachId") UUID coachId, @Param("athleteId") UUID athleteId,
                                       @Param("from") LocalDate from);

    /**
     * P7 : supprime les séances de ce coach pour cet athlète à partir de ce jour inclus, sauf celles dont la
     * réalisation est validée ; passé et autres coachs intacts. Un brouillon part avec sa séance (CASCADE).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from PlannedSession ps
            where ps.coach.id = :coachId and ps.athlete.id = :athleteId and ps.scheduledDate >= :from
              and not exists (select sp.id from SessionPerformance sp where sp.plannedSession = ps
                              and sp.status = com.athletiq.performance.PerformanceStatus.VALIDATED)
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
