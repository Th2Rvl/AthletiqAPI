package com.athletiq.planning;

/**
 * Ce que le calendrier de l'athlète montre d'une séance réalisée : son RPE ({@code null} = non renseigné), le
 * nombre de séries faites (répétitions renseignées) et la présence d'un commentaire.
 */
public record CompletionSummary(Integer rpe, int doneSetCount, boolean hasComment) {
}
