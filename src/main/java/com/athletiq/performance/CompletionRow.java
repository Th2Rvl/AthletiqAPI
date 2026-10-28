package com.athletiq.performance;

import java.util.UUID;

/** Ligne du résumé d'une réalisation validée : RPE, séries faites et nombre de commentaires (0 ou 1). */
public record CompletionRow(UUID plannedSessionId, Integer rpe, Long doneSetCount, Long commented) {
}
