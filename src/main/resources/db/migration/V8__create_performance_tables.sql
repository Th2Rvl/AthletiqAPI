-- Réalisé des séances planifiées (#7, Q1 à Q4). #7 pose le modèle et les lectures du coach ; l'athlète
-- l'écrit dans #10 (saisie en brouillon, validation, correction).

-- RM4 : une réalisation par séance planifiée (UNIQUE planned_session_id). Supprimée avec sa séance planifiée
-- (CASCADE) : P7 ne supprime jamais une séance à réalisation validée, mais un brouillon part avec la sienne.
-- RM3 : seule une réalisation VALIDATED est visible du coach ; un brouillon (DRAFT) lui reste invisible.
-- validated_at : première validation, présente si et seulement si la réalisation est validée.
-- corrected_at : dernière correction après validation, source de « Modifiée le … » (Q3) ; updated_at ne
-- convient pas, car il bouge aussi pendant la saisie du brouillon.
-- RM9 : RPE 1 à 10 et fatigue 1 à 5, entiers et facultatifs ; commentaire facultatif de 1000 caractères au plus.
CREATE TABLE session_performance
(
    id                 BINARY(16)    NOT NULL,
    planned_session_id BINARY(16)    NOT NULL,
    athlete_id         BINARY(16)    NOT NULL,
    status             VARCHAR(16)   NOT NULL,
    rpe                TINYINT       NULL,
    fatigue            TINYINT       NULL,
    comment            VARCHAR(1000) NULL,
    validated_at       DATETIME(6)   NULL,
    corrected_at       DATETIME(6)   NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    CONSTRAINT pk_session_performance PRIMARY KEY (id),
    CONSTRAINT uk_session_performance_planned_session UNIQUE (planned_session_id),
    CONSTRAINT fk_session_performance_planned_session FOREIGN KEY (planned_session_id)
        REFERENCES planned_session (id) ON DELETE CASCADE,
    CONSTRAINT fk_session_performance_athlete FOREIGN KEY (athlete_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_session_performance_status CHECK (status IN ('DRAFT', 'VALIDATED')),
    CONSTRAINT ck_session_performance_rpe CHECK (rpe IS NULL OR rpe BETWEEN 1 AND 10),
    CONSTRAINT ck_session_performance_fatigue CHECK (fatigue IS NULL OR fatigue BETWEEN 1 AND 5),
    CONSTRAINT ck_session_performance_validated CHECK ((status = 'VALIDATED') = (validated_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_session_performance_athlete_status ON session_performance (athlete_id, status);

-- Q2 : une ligne par série prévue de l'instantané, au plus une par réalisation (UNIQUE). reps NULL = série non
-- faite ; une série prévue sans ligne est aussi non faite. Mêmes bornes que session_snapshot_set (V7).
-- snapshot_set_id en RESTRICT : une série d'instantané réalisée ne disparaît pas sous la réalisation.
CREATE TABLE performed_set
(
    id              BINARY(16)    NOT NULL,
    performance_id  BINARY(16)    NOT NULL,
    snapshot_set_id BINARY(16)    NOT NULL,
    reps            INT           NULL,
    load_kg         DECIMAL(5, 1) NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    CONSTRAINT pk_performed_set PRIMARY KEY (id),
    CONSTRAINT uk_performed_set_snapshot_set UNIQUE (performance_id, snapshot_set_id),
    CONSTRAINT fk_performed_set_performance FOREIGN KEY (performance_id)
        REFERENCES session_performance (id) ON DELETE CASCADE,
    CONSTRAINT fk_performed_set_snapshot_set FOREIGN KEY (snapshot_set_id)
        REFERENCES session_snapshot_set (id) ON DELETE RESTRICT,
    CONSTRAINT ck_performed_set_reps CHECK (reps IS NULL OR reps BETWEEN 1 AND 100),
    CONSTRAINT ck_performed_set_load CHECK (load_kg IS NULL OR (load_kg >= 0 AND load_kg <= 999.5 AND MOD(load_kg * 2, 1) = 0))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX idx_performed_set_snapshot_set ON performed_set (snapshot_set_id);
