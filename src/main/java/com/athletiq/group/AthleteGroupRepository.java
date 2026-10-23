package com.athletiq.group;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AthleteGroupRepository extends JpaRepository<AthleteGroup, UUID> {

    /** Comparaison côté MySQL avec la collation ai_ci : insensible à la casse et aux accents. */
    boolean existsByCoachIdAndName(UUID coachId, String name);

    boolean existsByCoachIdAndNameAndIdNot(UUID coachId, String name, UUID id);

    Optional<AthleteGroup> findByCoachIdAndName(UUID coachId, String name);

    boolean existsByIdAndCoachId(UUID id, UUID coachId);

    Optional<AthleteGroup> findByIdAndCoachId(UUID id, UUID coachId);

    /** Verrouille le groupe pendant un ajout par lot : deux ajouts simultanés s'exécutent l'un après l'autre. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from AthleteGroup g where g.id = :id and g.coach.id = :coachId")
    Optional<AthleteGroup> findByIdAndCoachIdForUpdate(@Param("id") UUID id, @Param("coachId") UUID coachId);

    /** Groupes d'un coach (RM4 : filtrés en base), triés par nom, avec leur nombre de membres. */
    @Query(value = """
            select new com.athletiq.group.GroupSummary(g.id, g.name, count(m.id), g.createdAt)
            from AthleteGroup g
            left join AthleteGroupMember m on m.group = g
            where g.coach.id = :coachId
            group by g.id, g.name, g.createdAt
            order by g.name asc, g.id asc
            """,
            countQuery = "select count(g) from AthleteGroup g where g.coach.id = :coachId")
    Page<GroupSummary> findSummariesByCoachId(@Param("coachId") UUID coachId, Pageable pageable);
}
