package com.athletiq.group;

import com.athletiq.group.dto.GroupMemberResponse;
import com.athletiq.group.dto.GroupResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GroupMapper {

    /** Le nombre de membres est compté par le service et passé en paramètre. */
    @Mapping(target = "athleteCount", source = "athleteCount")
    GroupResponse toResponse(AthleteGroup group, long athleteCount);

    GroupResponse toResponse(GroupSummary summary);

    @Mapping(target = "athleteId", source = "athlete.id")
    @Mapping(target = "firstName", source = "athlete.firstName")
    @Mapping(target = "lastName", source = "athlete.lastName")
    @Mapping(target = "email", source = "athlete.email")
    GroupMemberResponse toMemberResponse(AthleteGroupMember member);
}
