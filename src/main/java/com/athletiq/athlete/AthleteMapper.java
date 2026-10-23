package com.athletiq.athlete;

import com.athletiq.athlete.dto.AthleteResponse;
import com.athletiq.athlete.dto.CoachLinkResponse;
import com.athletiq.athlete.dto.GroupRef;
import com.athletiq.athlete.dto.InvitationResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AthleteMapper {

    /** Le code en clair est déchiffré par le service et passé en paramètre : il n'est pas un champ de l'entité. */
    @Mapping(target = "code", source = "code")
    InvitationResponse toInvitationResponse(CoachInvitation invitation, String code);

    @Mapping(target = "coachId", source = "coach.id")
    @Mapping(target = "firstName", source = "coach.firstName")
    @Mapping(target = "lastName", source = "coach.lastName")
    CoachLinkResponse toCoachLinkResponse(CoachAthlete link);

    @Mapping(target = "id", source = "link.athlete.id")
    @Mapping(target = "firstName", source = "link.athlete.firstName")
    @Mapping(target = "lastName", source = "link.athlete.lastName")
    @Mapping(target = "email", source = "link.athlete.email")
    @Mapping(target = "joinedAt", source = "link.joinedAt")
    @Mapping(target = "groups", source = "groups")
    AthleteResponse toAthleteResponse(CoachAthlete link, List<GroupRef> groups);
}
