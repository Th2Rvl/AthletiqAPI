package com.athletiq.performance.dto;

import java.util.List;
import java.util.UUID;

/** Records personnels de l'athlète connecté, tous coachs confondus, triés par groupe musculaire puis nom. */
public record AthletePersonalRecordsResponse(UUID athleteId, List<AthletePersonalRecord> records) {
}
