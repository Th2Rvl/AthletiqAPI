package com.athletiq.performance.dto;

import java.util.List;
import java.util.UUID;

/** Records personnels d'un athlète avec ce coach (US31), triés par groupe musculaire (code) puis par nom. */
public record PersonalRecordsResponse(UUID athleteId, List<PersonalRecord> records) {
}
