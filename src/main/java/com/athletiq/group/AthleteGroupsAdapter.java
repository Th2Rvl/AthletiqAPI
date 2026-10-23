package com.athletiq.group;

import com.athletiq.athlete.AthleteGroupDirectory;
import com.athletiq.athlete.dto.GroupRef;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Fournit à {@code athlete} les groupes de ses athlètes, sans que {@code athlete} dépende de {@code group}. */
@Component
@RequiredArgsConstructor
class AthleteGroupsAdapter implements AthleteGroupDirectory {

    private final AthleteGroupMemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<GroupRef>> groupsByAthlete(UUID coachId, Collection<UUID> athleteIds) {
        if (athleteIds.isEmpty()) {
            return Map.of();
        }
        return memberRepository.findMemberships(coachId, athleteIds).stream()
                .collect(Collectors.groupingBy(GroupMembership::athleteId, LinkedHashMap::new,
                        Collectors.mapping(m -> new GroupRef(m.groupId(), m.groupName()), Collectors.toList())));
    }
}
