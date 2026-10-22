package com.athletiq.organisation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganisationInvitationRepository extends JpaRepository<OrganisationInvitation, UUID> {

    boolean existsByOrganisationIdAndEmailAndStatus(UUID organisationId, String email, InvitationStatus status);

    Optional<OrganisationInvitation> findByOrganisationIdAndEmailAndStatus(UUID organisationId, String email,
                                                                           InvitationStatus status);

    Optional<OrganisationInvitation> findByIdAndOrganisationId(UUID id, UUID organisationId);

    Page<OrganisationInvitation> findByOrganisationIdAndStatus(UUID organisationId, InvitationStatus status,
                                                               Pageable pageable);

    /** Invitations d'un e-mail dans toutes les organisations, avec l'organisation chargée. */
    @Query("""
            select i from OrganisationInvitation i
            join fetch i.organisation
            where i.email = :email and i.status = :status
            """)
    List<OrganisationInvitation> findAllByEmailAndStatus(@Param("email") String email,
                                                         @Param("status") InvitationStatus status);

    long countByOrganisationIdAndStatus(UUID organisationId, InvitationStatus status);

    @Query("""
            select new com.athletiq.organisation.OrganisationCount(i.organisation.id, count(i))
            from OrganisationInvitation i
            where i.organisation.id in :organisationIds and i.status = :status
            group by i.organisation.id
            """)
    List<OrganisationCount> countByOrganisationIdsAndStatus(
            @Param("organisationIds") Collection<UUID> organisationIds,
            @Param("status") InvitationStatus status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from OrganisationInvitation i where i.organisation.id = :organisationId")
    int deleteAllByOrganisationId(@Param("organisationId") UUID organisationId);
}
