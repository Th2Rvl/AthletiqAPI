package com.athletiq.performance;

/**
 * État d'une réalisation (Q3) : saisie en cours ({@code DRAFT}, invisible du coach) ou validée par l'athlète
 * ({@code VALIDATED}). Stocké par son nom, contrainte CHECK dans la migration V8.
 */
public enum PerformanceStatus {
    DRAFT,
    VALIDATED
}
