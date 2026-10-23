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

public interface OrganisationCoachRepository extends JpaRepository<OrganisationCoach, UUID> {

    boolean existsByOrganisationIdAndCoachId(UUID organisationId, UUID coachId);

    Optional<OrganisationCoach> findByOrganisationIdAndCoachId(UUID organisationId, UUID coachId);

    long countByOrganisationId(UUID organisationId);

    /** Coachs d'une organisation, triés par nom ; le coach est chargé dans la même requête (pas de N+1). */
    @Query(value = """
            select oc from OrganisationCoach oc
            join fetch oc.coach c
            where oc.organisation.id = :organisationId
            order by c.lastName asc, c.firstName asc, c.email asc
            """,
            countQuery = "select count(oc) from OrganisationCoach oc where oc.organisation.id = :organisationId")
    Page<OrganisationCoach> findPageByOrganisationId(@Param("organisationId") UUID organisationId,
                                                     Pageable pageable);

    @Query("""
            select new com.athletiq.organisation.OrganisationCount(oc.organisation.id, count(oc))
            from OrganisationCoach oc
            where oc.organisation.id in :organisationIds
            group by oc.organisation.id
            """)
    List<OrganisationCount> countByOrganisationIds(@Param("organisationIds") Collection<UUID> organisationIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from OrganisationCoach oc where oc.organisation.id = :organisationId")
    int deleteAllByOrganisationId(@Param("organisationId") UUID organisationId);
}
