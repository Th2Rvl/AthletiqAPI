package com.athletiq.performance.dto;

/**
 * Pourquoi un athlète est « À surveiller » (US32, Q14) : au moins {@code missed-threshold} séance non faite sur
 * les {@code missed-days} derniers jours, ou un taux de réalisation sous {@code min-completion-rate} % avec au
 * moins {@code min-past-sessions} séances passées (réglages {@code athletiq.kpi}).
 */
public enum WatchReason {
    MISSED_RECENTLY,
    LOW_COMPLETION
}
