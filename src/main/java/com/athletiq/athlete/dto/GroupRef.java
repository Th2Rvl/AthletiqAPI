package com.athletiq.athlete.dto;

import java.util.UUID;

/** Groupe du coach dont l'athlète est membre (colonne « Groupes » de la liste). */
public record GroupRef(UUID id, String name) {
}
