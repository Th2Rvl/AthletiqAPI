package com.athletiq.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Port {@code planning.PlannedSessionCompletion} : une requête par lot, aucune pour un lot vide. */
@ExtendWith(MockitoExtension.class)
class PlannedSessionCompletionAdapterTest {

    @Mock
    private SessionPerformanceRepository performanceRepository;

    @Test
    void answersTheValidatedSessionsAmongTheGivenOnes() {
        UUID done = UUID.randomUUID();
        UUID notDone = UUID.randomUUID();
        when(performanceRepository.findValidatedPlannedSessionIds(List.of(done, notDone))).thenReturn(List.of(done));

        assertThat(new PlannedSessionCompletionAdapter(performanceRepository).findValidated(List.of(done, notDone)))
                .containsExactly(done);
    }

    @Test
    void anEmptyBatchCostsNoQuery() {
        assertThat(new PlannedSessionCompletionAdapter(performanceRepository).findValidated(List.of())).isEmpty();
        verifyNoInteractions(performanceRepository);
    }

    @Test
    void summarizesTheValidatedSessionsWithRpeDoneSetsAndComment() {
        UUID commented = UUID.randomUUID();
        UUID silent = UUID.randomUUID();
        when(performanceRepository.summarizeValidated(List.of(commented, silent))).thenReturn(List.of(
                new CompletionRow(commented, 8, 13L, 1L), new CompletionRow(silent, null, 0L, 0L)));

        var summaries = new PlannedSessionCompletionAdapter(performanceRepository)
                .summarize(List.of(commented, silent));

        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(commented).rpe()).isEqualTo(8);
        assertThat(summaries.get(commented).doneSetCount()).isEqualTo(13);
        assertThat(summaries.get(commented).hasComment()).isTrue();
        assertThat(summaries.get(silent).rpe()).isNull();
        assertThat(summaries.get(silent).hasComment()).isFalse();
    }

    @Test
    void anEmptySummaryBatchCostsNoQuery() {
        assertThat(new PlannedSessionCompletionAdapter(performanceRepository).summarize(List.of())).isEmpty();
        verifyNoInteractions(performanceRepository);
    }
}
