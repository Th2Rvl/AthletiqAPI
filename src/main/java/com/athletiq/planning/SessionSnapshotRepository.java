package com.athletiq.planning;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionSnapshotRepository extends JpaRepository<SessionSnapshot, UUID> {

    /**
     * Détail, première des deux requêtes : l'instantané et ses exercices. Les séries viennent de
     * {@link SessionSnapshotExerciseRepository#fetchSetsOfSnapshot}, pour éviter le produit cartésien.
     */
    @Query("""
            select distinct s from SessionSnapshot s
            left join fetch s.exercises
            where s.id = :id
            """)
    Optional<SessionSnapshot> findDetailById(@Param("id") UUID id);

    /**
     * Supprime, parmi ces instantanés, ceux qu'aucune séance planifiée ne référence plus. La base supprime
     * leurs exercices et séries en cascade ; la clé {@code RESTRICT} empêcherait d'en supprimer un encore utilisé.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from SessionSnapshot s
            where s.id in :ids
              and not exists (select ps.id from PlannedSession ps where ps.snapshot.id = s.id)
            """)
    int deleteOrphans(@Param("ids") Collection<UUID> ids);
}
