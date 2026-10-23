package com.athletiq.planning;

import java.util.UUID;

/** Nombre d'exercices et de séries d'un instantané, agrégé en base. */
public record SnapshotCount(UUID snapshotId, long exerciseCount, long setCount) {
}
