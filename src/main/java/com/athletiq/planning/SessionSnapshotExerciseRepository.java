package com.athletiq.planning;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionSnapshotExerciseRepository extends JpaRepository<SessionSnapshotExercise, UUID> {

    /** Détail, seconde requête : initialise les séries des exercices déjà chargés (même contexte de persistance). */
    @Query("""
            select distinct e from SessionSnapshotExercise e
            left join fetch e.sets
            where e.snapshot.id = :snapshotId
            """)
    List<SessionSnapshotExercise> fetchSetsOfSnapshot(@Param("snapshotId") UUID snapshotId);

    /** Noms des exercices d'un instantané, dans l'ordre de la séance. */
    @Query("select e.exerciseName from SessionSnapshotExercise e where e.snapshot.id = :snapshotId order by e.position")
    List<String> findNamesBySnapshot(@Param("snapshotId") UUID snapshotId);

    /** Calendrier : nombre d'exercices et de séries de chaque instantané, en une requête agrégée. */
    @Query("""
            select new com.athletiq.planning.SnapshotCount(e.snapshot.id, count(distinct e.id), count(st.id))
            from SessionSnapshotExercise e
            left join e.sets st
            where e.snapshot.id in :snapshotIds
            group by e.snapshot.id
            """)
    List<SnapshotCount> countBySnapshot(@Param("snapshotIds") Collection<UUID> snapshotIds);
}
