package com.athletiq.organisation;

public enum InvitationStatus {
    /** En attente de la création du compte coach. */
    PENDING,
    /** Le coach est rattaché à l'organisation. */
    ACCEPTED,
    /** Annulée par un administrateur. */
    CANCELLED
}
