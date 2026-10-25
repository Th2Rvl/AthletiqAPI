package com.athletiq.performance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Formules du suivi (RM8), sans état ni base. Les kilos sont des {@link BigDecimal} à une décimale ; un
 * pourcentage est un entier arrondi {@code HALF_UP} ; une division par zéro donne {@code null}, jamais 0.
 */
final class PerformanceMath {

    static final int KG_SCALE = 1;
    static final BigDecimal ZERO_KG = BigDecimal.ZERO.setScale(KG_SCALE, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PerformanceMath() {
    }

    /** Une valeur en kg à une décimale, ou {@code null}. */
    static BigDecimal kg(BigDecimal value) {
        return value == null ? null : value.setScale(KG_SCALE, RoundingMode.HALF_UP);
    }

    /** Volume d'une série, {@code reps × charge} ; {@code null} si la charge est vide (exclue des volumes). */
    static BigDecimal setVolume(Integer reps, BigDecimal loadKg) {
        if (reps == null || loadKg == null) {
            return null;
        }
        return kg(loadKg.multiply(BigDecimal.valueOf(reps)));
    }

    /**
     * Écart du réalisé au prévu en %, {@code (réalisé - prévu) / prévu × 100} arrondi à l'entier {@code HALF_UP} ;
     * {@code null} si l'une des valeurs manque ou si le prévu vaut 0.
     */
    static Integer deltaPercent(BigDecimal planned, BigDecimal performed) {
        if (planned == null || performed == null || planned.signum() == 0) {
            return null;
        }
        return performed.subtract(planned).multiply(HUNDRED)
                .divide(planned, 0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    /**
     * Taux de réalisation, {@code réalisées / (réalisées + non faites) × 100} arrondi à l'entier {@code HALF_UP} ;
     * {@code null} sans séance passée (le front affiche « - »).
     */
    static Integer completionRate(long done, long missed) {
        long past = done + missed;
        if (past == 0) {
            return null;
        }
        return BigDecimal.valueOf(done).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(past), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    /** Moyenne à une décimale {@code HALF_UP} (RPE moyen) ; {@code null} si aucune valeur. */
    static BigDecimal average(long sum, long count) {
        if (count == 0) {
            return null;
        }
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
    }
}
