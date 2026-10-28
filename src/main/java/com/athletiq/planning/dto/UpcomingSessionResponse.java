package com.athletiq.planning.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Prochaine séance planifiée après aujourd'hui, avec le nom de ses exercices dans l'ordre. */
public record UpcomingSessionResponse(UUID id, LocalDate date, String name, List<String> exerciseNames) {
}
