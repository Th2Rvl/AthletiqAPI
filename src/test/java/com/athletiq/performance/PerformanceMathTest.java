package com.athletiq.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Formules du suivi (RM8) : kilos à une décimale, pourcentages entiers HALF_UP, division par zéro = null. */
class PerformanceMathTest {

    private static BigDecimal kg(String value) {
        return new BigDecimal(value);
    }

    @Test
    void aWeightIsKeptWithOneDecimal() {
        assertThat(PerformanceMath.kg(kg("60"))).isEqualTo(kg("60.0"));
        assertThat(PerformanceMath.kg(kg("62.50"))).isEqualTo(kg("62.5"));
        assertThat(PerformanceMath.kg(null)).isNull();
    }

    @Test
    void theVolumeOfASetIsRepsTimesLoadAndAnEmptyLoadIsExcluded() {
        assertThat(PerformanceMath.setVolume(8, kg("62.5"))).isEqualTo(kg("500.0"));
        assertThat(PerformanceMath.setVolume(8, kg("0.0"))).isEqualTo(kg("0.0"));
        assertThat(PerformanceMath.setVolume(8, null)).isNull();
        assertThat(PerformanceMath.setVolume(null, kg("60.0"))).isNull();
    }

    @Test
    void theDeltaPercentIsRoundedHalfUpToAnInteger() {
        // (1110 - 1000) / 1000 = +11 % ; (995 - 1000) / 1000 = -0,5 % -> -1 (HALF_UP s'éloigne de zéro).
        assertThat(PerformanceMath.deltaPercent(kg("1000.0"), kg("1110.0"))).isEqualTo(11);
        assertThat(PerformanceMath.deltaPercent(kg("1000.0"), kg("995.0"))).isEqualTo(-1);
        assertThat(PerformanceMath.deltaPercent(kg("1000.0"), kg("1004.9"))).isEqualTo(0);
        assertThat(PerformanceMath.deltaPercent(kg("1000.0"), kg("1005.0"))).isEqualTo(1);
        // Tout non fait : -100 %.
        assertThat(PerformanceMath.deltaPercent(kg("1240.0"), kg("0.0"))).isEqualTo(-100);
        // 2/3 = 66,67 % d'écart.
        assertThat(PerformanceMath.deltaPercent(kg("300.0"), kg("500.0"))).isEqualTo(67);
    }

    @Test
    void theDeltaPercentIsNullWithoutAPlannedVolumeOrWhenItIsZero() {
        assertThat(PerformanceMath.deltaPercent(null, kg("100.0"))).isNull();
        assertThat(PerformanceMath.deltaPercent(kg("0.0"), kg("100.0"))).isNull();
        assertThat(PerformanceMath.deltaPercent(kg("100.0"), null)).isNull();
    }

    @Test
    void theCompletionRateIsDoneOverPastSessionsRoundedHalfUp() {
        assertThat(PerformanceMath.completionRate(2, 1)).isEqualTo(67);
        assertThat(PerformanceMath.completionRate(1, 2)).isEqualTo(33);
        assertThat(PerformanceMath.completionRate(1, 7)).isEqualTo(13);
        assertThat(PerformanceMath.completionRate(1, 1)).isEqualTo(50);
        assertThat(PerformanceMath.completionRate(3, 0)).isEqualTo(100);
        assertThat(PerformanceMath.completionRate(0, 4)).isZero();
    }

    @Test
    void withoutPastSessionTheCompletionRateIsNullNotZero() {
        assertThat(PerformanceMath.completionRate(0, 0)).isNull();
    }

    @Test
    void theAverageRpeHasOneDecimalRoundedHalfUpAndIsNullWithoutValue() {
        assertThat(PerformanceMath.average(15, 2)).isEqualTo(new BigDecimal("7.5"));
        assertThat(PerformanceMath.average(23, 3)).isEqualTo(new BigDecimal("7.7"));
        assertThat(PerformanceMath.average(25, 4)).isEqualTo(new BigDecimal("6.3"));
        assertThat(PerformanceMath.average(14, 2)).isEqualTo(new BigDecimal("7.0"));
        assertThat(PerformanceMath.average(0, 0)).isNull();
    }
}
