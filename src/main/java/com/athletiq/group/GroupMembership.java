package com.athletiq.group;

import java.util.UUID;

/** Projection « tel athlète est membre de tel groupe » pour la colonne « Groupes » de la liste des athlètes. */
public record GroupMembership(UUID athleteId, UUID groupId, String groupName) {
}
