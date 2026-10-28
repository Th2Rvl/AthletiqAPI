package com.athletiq.athlete;

import com.athletiq.athlete.dto.AthleteRef;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoachAthleteRepository extends JpaRepository<CoachAthlete, UUID> {

    boolean existsByAthleteIdAndEndedAtIsNull(UUID athleteId);

    boolean existsByCoachIdAndAthleteIdAndEndedAtIsNull(UUID coachId, UUID athleteId);

    /** Coachs qui suivent activement cet athlète, du plus ancien lien au plus récent. */
    @Query("""
            select l from CoachAthlete l
            join fetch l.coach
            where l.athlete.id = :athleteId and l.endedAt is null
            order by l.joinedAt asc, l.id asc
            """)
    List<CoachAthlete> findActiveByAthleteId(@Param("athleteId") UUID athleteId);

    Optional<CoachAthlete> findByCoachIdAndAthleteIdAndEndedAtIsNull(UUID coachId, UUID athleteId);

    /** Nombre d'athlètes de la liste suivis activement par ce coach : une seule requête pour tout un lot. */
    @Query("""
            select count(distinct l.athlete.id) from CoachAthlete l
            where l.coach.id = :coachId and l.endedAt is null and l.athlete.id in :athleteIds
            """)
    long countActiveByCoachIdAndAthleteIds(@Param("coachId") UUID coachId,
                                           @Param("athleteIds") Collection<UUID> athleteIds);

    /**
     * Tous les athlètes suivis activement par ce coach, membres de ce groupe s'il est donné, triés par nom puis
     * prénom (collation ai_ci), sans pagination : le tableau de bord (#7) en montre chacun. Le groupe est
     * aussi filtré par coach, en plus du contrôle de propriété fait avant.
     */
    @Query("""
            select new com.athletiq.athlete.dto.AthleteRef(a.id, a.firstName, a.lastName)
            from CoachAthlete l
            join l.athlete a
            where l.coach.id = :coachId and l.endedAt is null
              and (:groupId is null or exists (
                   select m.id from AthleteGroupMember m
                   where m.group.id = :groupId and m.group.coach.id = :coachId and m.athlete = a))
            order by a.lastName asc, a.firstName asc, a.id asc
            """)
    List<AthleteRef> findFollowedRefs(@Param("coachId") UUID coachId, @Param("groupId") UUID groupId);

    /**
     * Athlètes suivis par ce coach (RM4 : filtrés par coach en base), triés par nom puis prénom.
     * {@code pattern} est un motif LIKE déjà échappé (caractère d'échappement {@code !}) ou
     * {@code null} ; les colonnes de {@code app_user} sont en {@code utf8mb4_0900_ai_ci}, donc la
     * recherche ignore la casse et les accents. {@code groupId} garde les membres d'un groupe,
     * {@code excludeGroupId} les athlètes qui n'en font pas partie (sélecteur d'ajout, D7).
     */
    @Query(value = """
            select l from CoachAthlete l
            join fetch l.athlete a
            where l.coach.id = :coachId and l.endedAt is null
              and (:pattern is null
                   or a.firstName like :pattern escape '!'
                   or a.lastName like :pattern escape '!'
                   or a.email like :pattern escape '!')
              and (:groupId is null or exists (
                   select m.id from AthleteGroupMember m where m.group.id = :groupId and m.athlete = a))
              and (:excludeGroupId is null or not exists (
                   select m.id from AthleteGroupMember m where m.group.id = :excludeGroupId and m.athlete = a))
            order by a.lastName asc, a.firstName asc, a.id asc
            """,
            countQuery = """
            select count(l) from CoachAthlete l
            join l.athlete a
            where l.coach.id = :coachId and l.endedAt is null
              and (:pattern is null
                   or a.firstName like :pattern escape '!'
                   or a.lastName like :pattern escape '!'
                   or a.email like :pattern escape '!')
              and (:groupId is null or exists (
                   select m.id from AthleteGroupMember m where m.group.id = :groupId and m.athlete = a))
              and (:excludeGroupId is null or not exists (
                   select m.id from AthleteGroupMember m where m.group.id = :excludeGroupId and m.athlete = a))
            """)
    Page<CoachAthlete> searchActiveByCoachId(@Param("coachId") UUID coachId,
                                             @Param("pattern") String pattern,
                                             @Param("groupId") UUID groupId,
                                             @Param("excludeGroupId") UUID excludeGroupId,
                                             Pageable pageable);
}
