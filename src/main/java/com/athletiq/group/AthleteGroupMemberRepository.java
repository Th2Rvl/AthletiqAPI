package com.athletiq.group;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AthleteGroupMemberRepository extends JpaRepository<AthleteGroupMember, UUID> {

    long countByGroupId(UUID groupId);

    /** Composition d'un groupe, triée par nom puis prénom ; l'athlète est chargé dans la même requête. */
    @Query(value = """
            select m from AthleteGroupMember m
            join fetch m.athlete a
            where m.group.id = :groupId
            order by a.lastName asc, a.firstName asc, a.id asc
            """,
            countQuery = "select count(m) from AthleteGroupMember m where m.group.id = :groupId")
    Page<AthleteGroupMember> findPageByGroupId(@Param("groupId") UUID groupId, Pageable pageable);

    @Query("""
            select m.athlete.id from AthleteGroupMember m
            where m.group.id = :groupId and m.athlete.id in :athleteIds
            """)
    List<UUID> findAthleteIdsByGroupIdAndAthleteIdIn(@Param("groupId") UUID groupId,
                                                    @Param("athleteIds") Collection<UUID> athleteIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AthleteGroupMember m where m.group.id = :groupId and m.athlete.id = :athleteId")
    int deleteByGroupIdAndAthleteId(@Param("groupId") UUID groupId, @Param("athleteId") UUID athleteId);

    /** Retire un athlète de tous les groupes d'un coach (RM5), sans toucher aux groupes des autres coachs. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from AthleteGroupMember m
            where m.athlete.id = :athleteId
              and m.group.id in (select g.id from AthleteGroup g where g.coach.id = :coachId)
            """)
    int deleteByCoachIdAndAthleteId(@Param("coachId") UUID coachId, @Param("athleteId") UUID athleteId);

    /** Groupes de ce coach dont ces athlètes sont membres, triés par nom (une requête pour toute une page). */
    @Query("""
            select new com.athletiq.group.GroupMembership(m.athlete.id, g.id, g.name)
            from AthleteGroupMember m
            join m.group g
            where g.coach.id = :coachId and m.athlete.id in :athleteIds
            order by g.name asc, g.id asc
            """)
    List<GroupMembership> findMemberships(@Param("coachId") UUID coachId,
                                         @Param("athleteIds") Collection<UUID> athleteIds);
}
