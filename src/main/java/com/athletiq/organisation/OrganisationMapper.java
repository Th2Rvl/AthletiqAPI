package com.athletiq.organisation;

import com.athletiq.organisation.dto.InvitationResponse;
import com.athletiq.organisation.dto.OrganisationCoachResponse;
import com.athletiq.organisation.dto.OrganisationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrganisationMapper {

    /** Les compteurs sont calculés par le service (requêtes groupées) et passés en paramètres. */
    @Mapping(target = "coachCount", source = "coachCount")
    @Mapping(target = "pendingInvitationCount", source = "pendingInvitationCount")
    @Mapping(target = "athleteCount", constant = "0L")
    OrganisationResponse toResponse(Organisation organisation, long coachCount, long pendingInvitationCount);

    @Mapping(target = "coachId", source = "coach.id")
    @Mapping(target = "firstName", source = "coach.firstName")
    @Mapping(target = "lastName", source = "coach.lastName")
    @Mapping(target = "email", source = "coach.email")
    OrganisationCoachResponse toCoachResponse(OrganisationCoach membership);

    @Mapping(target = "organisationId", source = "organisation.id")
    @Mapping(target = "coachId", source = "coach.id")
    InvitationResponse toInvitationResponse(OrganisationInvitation invitation);
}
