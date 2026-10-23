-- Séances planifiées (#6, US21-US26). Plusieurs séances par athlète et par jour sont permises (Q1, décision du PO) :
-- AUCUN UNIQUE sur (athlete_id, scheduled_date).
-- Instantané partagé immuable (Q2) : copie du modèle (#5) référencée par une ou plusieurs séances planifiées
-- (groupe, duplication). Supprimé par le service quand plus aucune séance planifiée ne le référence.
CREATE TABLE session_snapshot
(
    id          BINARY(16)    NOT NULL,
    coach_id    BINARY(16)    NOT NULL,
    name        VARCHAR(100)  NOT NULL,
    description VARCHAR(1000) NULL,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    CONSTRAINT pk_session_snapshot PRIMARY KEY (id),
    CONSTRAINT fk_session_snapshot_coach FOREIGN KEY (coach_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_session_snapshot_coach ON session_snapshot (coach_id);

-- exercise_id en RESTRICT (Q11) : un exercice présent dans une séance planifiée ne se supprime pas.
-- exercise_name et muscle_group sont copiés : l'instantané reste lisible si l'exercice est renommé.
-- exercise_name fait 200 caractères comme exercise.name (V5) : à 100, la copie d'un nom long échouerait.
CREATE TABLE session_snapshot_exercise
(
    id            BINARY(16)   NOT NULL,
    snapshot_id   BINARY(16)   NOT NULL,
    exercise_id   BINARY(16)   NOT NULL,
    exercise_name VARCHAR(200) NOT NULL,
    muscle_group  VARCHAR(50)  NOT NULL,
    position      INT          NOT NULL,
    note          VARCHAR(500) NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_session_snapshot_exercise PRIMARY KEY (id),
    CONSTRAINT uk_session_snapshot_exercise_position UNIQUE (snapshot_id, position),
    CONSTRAINT fk_session_snapshot_exercise_snapshot FOREIGN KEY (snapshot_id)
        REFERENCES session_snapshot (id) ON DELETE CASCADE,
    CONSTRAINT fk_session_snapshot_exercise_exercise FOREIGN KEY (exercise_id)
        REFERENCES exercise (id) ON DELETE RESTRICT,
    CONSTRAINT ck_session_snapshot_exercise_position CHECK (position BETWEEN 0 AND 29)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_session_snapshot_exercise_exercise ON session_snapshot_exercise (exercise_id);

-- Mêmes bornes que planned_set (V6).
CREATE TABLE session_snapshot_set
(
    id                   BINARY(16)    NOT NULL,
    snapshot_exercise_id BINARY(16)    NOT NULL,
    position             INT           NOT NULL,
    reps                 INT           NOT NULL,
    load_kg              DECIMAL(5, 1) NULL,
    rest_seconds         INT           NULL,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    CONSTRAINT pk_session_snapshot_set PRIMARY KEY (id),
    CONSTRAINT uk_session_snapshot_set_position UNIQUE (snapshot_exercise_id, position),
    CONSTRAINT fk_session_snapshot_set_exercise FOREIGN KEY (snapshot_exercise_id)
        REFERENCES session_snapshot_exercise (id) ON DELETE CASCADE,
    CONSTRAINT ck_session_snapshot_set_position CHECK (position BETWEEN 0 AND 19),
    CONSTRAINT ck_session_snapshot_set_reps CHECK (reps BETWEEN 1 AND 100),
    CONSTRAINT ck_session_snapshot_set_load CHECK (load_kg IS NULL OR (load_kg >= 0 AND load_kg <= 999.5 AND MOD(load_kg * 2, 1) = 0)),
    CONSTRAINT ck_session_snapshot_set_rest CHECK (rest_seconds IS NULL OR rest_seconds BETWEEN 0 AND 600)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- source_workout_id et group_id servent à l'affichage seulement : SET NULL à la suppression du modèle
-- (l'instantané garde le contenu) ou du groupe (P8 de #3 : les séances des anciens membres restent).
CREATE TABLE planned_session
(
    id                BINARY(16)  NOT NULL,
    coach_id          BINARY(16)  NOT NULL,
    athlete_id        BINARY(16)  NOT NULL,
    scheduled_date    DATE        NOT NULL,
    snapshot_id       BINARY(16)  NOT NULL,
    source_workout_id BINARY(16)  NULL,
    group_id          BINARY(16)  NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    CONSTRAINT pk_planned_session PRIMARY KEY (id),
    CONSTRAINT fk_planned_session_coach FOREIGN KEY (coach_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_planned_session_athlete FOREIGN KEY (athlete_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_planned_session_snapshot FOREIGN KEY (snapshot_id) REFERENCES session_snapshot (id) ON DELETE RESTRICT,
    CONSTRAINT fk_planned_session_workout FOREIGN KEY (source_workout_id) REFERENCES workout (id) ON DELETE SET NULL,
    CONSTRAINT fk_planned_session_group FOREIGN KEY (group_id) REFERENCES athlete_group (id) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_planned_session_coach_athlete_date ON planned_session (coach_id, athlete_id, scheduled_date);
CREATE INDEX idx_planned_session_group_date ON planned_session (group_id, scheduled_date);
CREATE INDEX idx_planned_session_snapshot ON planned_session (snapshot_id);
CREATE INDEX idx_planned_session_athlete ON planned_session (athlete_id);
CREATE INDEX idx_planned_session_workout ON planned_session (source_workout_id);
