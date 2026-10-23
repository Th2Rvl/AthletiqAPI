-- Séances du coach (#5, US18-US20) : séance -> exercices de séance ordonnés -> séries prévues ordonnées.
-- Une séance appartient à un seul coach (filtre par coach_id). Son nom est unique par coach, sans tenir
-- compte de la casse ni des accents (collation ai_ci, comme exercise et athlete_group).
-- Les positions sont l'index dans la liste envoyée (0..n-1) : uniques par parent, bornées par CHECK.
-- revision est interne : le PUT l'incrémente pour que updated_at bouge même si seules les séries changent.
CREATE TABLE workout
(
    id          BINARY(16)                              NOT NULL,
    coach_id    BINARY(16)                              NOT NULL,
    name        VARCHAR(100) COLLATE utf8mb4_0900_ai_ci NOT NULL,
    description VARCHAR(1000)                           NULL,
    revision    INT                                     NOT NULL DEFAULT 0,
    created_at  DATETIME(6)                             NOT NULL,
    updated_at  DATETIME(6)                             NOT NULL,
    CONSTRAINT pk_workout PRIMARY KEY (id),
    CONSTRAINT uk_workout_coach_name UNIQUE (coach_id, name),
    CONSTRAINT fk_workout_coach FOREIGN KEY (coach_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_workout_coach_updated ON workout (coach_id, updated_at);

-- exercise_id en RESTRICT : un exercice utilisé dans une séance ne peut pas être supprimé (RM9, Q15).
CREATE TABLE workout_exercise
(
    id          BINARY(16)   NOT NULL,
    workout_id  BINARY(16)   NOT NULL,
    exercise_id BINARY(16)   NOT NULL,
    position    INT          NOT NULL,
    note        VARCHAR(500) NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_workout_exercise PRIMARY KEY (id),
    CONSTRAINT uk_workout_exercise_position UNIQUE (workout_id, position),
    CONSTRAINT fk_workout_exercise_workout FOREIGN KEY (workout_id) REFERENCES workout (id) ON DELETE CASCADE,
    CONSTRAINT fk_workout_exercise_exercise FOREIGN KEY (exercise_id) REFERENCES exercise (id) ON DELETE RESTRICT,
    CONSTRAINT ck_workout_exercise_position CHECK (position BETWEEN 0 AND 29)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_workout_exercise_exercise ON workout_exercise (exercise_id);

-- Une ligne par série (Q1) : répétitions 1..100, charge en kg au pas de 0,5 (vide = poids du corps),
-- repos en secondes 0..600 (facultatif).
CREATE TABLE planned_set
(
    id                  BINARY(16)   NOT NULL,
    workout_exercise_id BINARY(16)   NOT NULL,
    position            INT          NOT NULL,
    reps                INT          NOT NULL,
    load_kg             DECIMAL(5, 1) NULL,
    rest_seconds        INT          NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    CONSTRAINT pk_planned_set PRIMARY KEY (id),
    CONSTRAINT uk_planned_set_position UNIQUE (workout_exercise_id, position),
    CONSTRAINT fk_planned_set_workout_exercise FOREIGN KEY (workout_exercise_id)
        REFERENCES workout_exercise (id) ON DELETE CASCADE,
    CONSTRAINT ck_planned_set_position CHECK (position BETWEEN 0 AND 19),
    CONSTRAINT ck_planned_set_reps CHECK (reps BETWEEN 1 AND 100),
    CONSTRAINT ck_planned_set_load CHECK (load_kg IS NULL OR (load_kg >= 0 AND load_kg <= 999.5 AND MOD(load_kg * 2, 1) = 0)),
    CONSTRAINT ck_planned_set_rest CHECK (rest_seconds IS NULL OR rest_seconds BETWEEN 0 AND 600)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
