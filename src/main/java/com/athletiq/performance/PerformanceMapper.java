package com.athletiq.performance;

import com.athletiq.performance.dto.SessionFeedback;
import com.athletiq.performance.dto.SessionHistoryItem;
import com.athletiq.planning.SessionStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Lignes lues en base vers les DTO du suivi. Les lectures ne chargent pas d'entité : elles projettent des
 * lignes, que ce mapper convertit ; les calculs (statut, volumes, écarts) restent dans le service.
 */
@Mapper(componentModel = "spring")
public interface PerformanceMapper {

    /** Le statut est calculé par le service (DONE ou MISSED) et passé en paramètre. */
    @Mapping(target = "status", source = "status")
    SessionHistoryItem toHistoryItem(SessionHistoryRow row, SessionStatus status);

    SessionFeedback toFeedback(ReviewHeaderRow header);
}
