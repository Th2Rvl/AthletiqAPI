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
}
