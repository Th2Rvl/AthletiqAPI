package com.athletiq.planning;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.athletiq.athlete.AthleteUnfollowedEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** P7 (RM10) : seules les séances à venir de ce coach pour cet athlète sont supprimées, puis les orphelins. */
@ExtendWith(MockitoExtension.class)
class PlanningAthleteUnfollowedListenerTest {

    /** 23 h 30 UTC le 4 novembre = 0 h 30 le 5 novembre à Paris. */
    private static final Instant NOW = Instant.parse("2026-11-04T23:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @Mock
    private PlannedSessionRepository plannedSessionRepository;
    @Mock
    private SessionSnapshotRepository snapshotRepository;

    private PlanningAthleteUnfollowedListener listener;
    private final UUID coachId = UUID.randomUUID();
    private final UUID athleteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new PlanningAthleteUnfollowedListener(plannedSessionRepository, snapshotRepository,
                new PlanningClock(Clock.fixed(NOW, ZoneOffset.UTC), new PlanningProperties(null)));
    }

    @Test
    void deletesTheUpcomingSessionsFromTodayInParisThenTheOrphanSnapshots() {
        List<UUID> snapshots = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(plannedSessionRepository.findUpcomingSnapshotIds(coachId, athleteId, TODAY)).thenReturn(snapshots);

        listener.onAthleteUnfollowed(new AthleteUnfollowedEvent(coachId, athleteId));

        InOrder order = inOrder(plannedSessionRepository, snapshotRepository);
        order.verify(plannedSessionRepository).deleteUpcoming(coachId, athleteId, TODAY);
        order.verify(snapshotRepository).deleteOrphans(snapshots);
    }

    @Test
    void doesNothingWithoutUpcomingSessions() {
        when(plannedSessionRepository.findUpcomingSnapshotIds(coachId, athleteId, TODAY)).thenReturn(List.of());

        listener.onAthleteUnfollowed(new AthleteUnfollowedEvent(coachId, athleteId));

        verify(plannedSessionRepository, never()).deleteUpcoming(any(), any(), any());
        verifyNoInteractions(snapshotRepository);
    }
}
