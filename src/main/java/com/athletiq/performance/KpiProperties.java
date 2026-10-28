package com.athletiq.performance;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Seuils de l'alerte « À surveiller » du tableau de bord ({@code athletiq.kpi}, Q14) : un athlète est signalé
 * s'il a au moins {@code missedThreshold} séance non faite sur les {@code missedDays} derniers jours (aujourd'hui
 * exclu), ou un taux de réalisation sous {@code minCompletionRate} % avec au moins {@code minPastSessions}
 * séances passées. Des valeurs incohérentes empêchent l'application de démarrer.
 *
 * @param missedDays        fenêtre des non faites récentes, de 1 à 28 jours (la plus courte période du tableau)
 * @param missedThreshold   nombre de non faites récentes qui déclenche l'alerte, au moins 1
 * @param minCompletionRate taux de réalisation minimal, en %, de 0 à 100
 * @param minPastSessions   séances passées nécessaires pour juger le taux, au moins 1
 */
@ConfigurationProperties("athletiq.kpi")
public record KpiProperties(
        @DefaultValue("7") int missedDays,
        @DefaultValue("1") int missedThreshold,
        @DefaultValue("70") int minCompletionRate,
        @DefaultValue("3") int minPastSessions) {

    public KpiProperties {
        require(missedDays >= 1 && missedDays <= 28, "athletiq.kpi.missed-days doit être compris entre 1 et 28");
        require(missedThreshold >= 1, "athletiq.kpi.missed-threshold doit valoir au moins 1");
        require(minCompletionRate >= 0 && minCompletionRate <= 100,
                "athletiq.kpi.min-completion-rate doit être compris entre 0 et 100");
        require(minPastSessions >= 1, "athletiq.kpi.min-past-sessions doit valoir au moins 1");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
