package com.athletiq.athlete;

import com.athletiq.user.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoachInvitationRepository extends JpaRepository<CoachInvitation, UUID> {

    boolean existsByCodeHash(String codeHash);

    /** L'invitation d'un code saisi, avec son coach chargé (pour le lien et la réponse). */
    @Query("select i from CoachInvitation i join fetch i.coach where i.codeHash = :codeHash")
    Optional<CoachInvitation> findByCodeHash(@Param("codeHash") String codeHash);

    /** Invitations en attente d'un coach, de la plus récente à la plus ancienne. */
    @Query(value = """
            select i from CoachInvitation i
            where i.coach.id = :coachId
              and i.revokedAt is null and i.consumedAt is null and i.expiresAt > :now
            order by i.createdAt desc, i.id desc
            """,
            countQuery = """
            select count(i) from CoachInvitation i
            where i.coach.id = :coachId
              and i.revokedAt is null and i.consumedAt is null and i.expiresAt > :now
            """)
    Page<CoachInvitation> findPendingByCoachId(@Param("coachId") UUID coachId, @Param("now") Instant now,
                                               Pageable pageable);

    @Query("""
            select i from CoachInvitation i
            where i.id = :id and i.coach.id = :coachId
              and i.revokedAt is null and i.consumedAt is null and i.expiresAt > :now
            """)
    Optional<CoachInvitation> findPendingByIdAndCoachId(@Param("id") UUID id, @Param("coachId") UUID coachId,
                                                        @Param("now") Instant now);

    /**
     * Consomme le code s'il est encore en attente. L'UPDATE conditionnel est atomique : si deux
     * athlètes saisissent le même code en même temps, un seul obtient 1 ligne modifiée.
     *
     * <p>Le contexte de persistance n'est pas vidé : le coach chargé avant reste utilisable.
     *
     * @return 1 si le code a été consommé, 0 s'il ne l'était plus
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update CoachInvitation i
            set i.consumedAt = :now, i.consumedBy = :athlete, i.updatedAt = :now
            where i.codeHash = :codeHash
              and i.consumedAt is null and i.revokedAt is null and i.expiresAt > :now
            """)
    int consume(@Param("codeHash") String codeHash, @Param("athlete") User athlete, @Param("now") Instant now);
}
