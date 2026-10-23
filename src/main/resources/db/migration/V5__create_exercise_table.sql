-- Bibliothèque d'exercices privée par coach (#4, US15-US17).
-- Un exercice appartient à un seul coach (app_user) ; un coach ne voit que les siens (filtre par coach_id).
-- Unicité : un coach ne peut pas avoir deux exercices au même nom, sans tenir compte de la casse ni des accents
-- (collation ai_ci, comme athlete_group). L'index unique couvre aussi les recherches par coach_id.
CREATE TABLE exercise
(
    id           BINARY(16)                             NOT NULL,
    coach_id     BINARY(16)                             NOT NULL,
    name         VARCHAR(200) COLLATE utf8mb4_0900_ai_ci NOT NULL,
    description  VARCHAR(1000)                          NULL,
    muscle_group VARCHAR(50)                            NOT NULL,
    equipment    VARCHAR(100)                           NULL,
    video_url    VARCHAR(500)                           NULL,
    created_at   DATETIME(6)                            NOT NULL,
    updated_at   DATETIME(6)                            NOT NULL,
    CONSTRAINT pk_exercise PRIMARY KEY (id),
    CONSTRAINT uk_exercise_coach_name UNIQUE (coach_id, name),
    CONSTRAINT fk_exercise_coach FOREIGN KEY (coach_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_exercise_muscle_group CHECK (muscle_group IN (
        'PECTORAUX', 'DOS', 'EPAULES', 'BICEPS', 'TRICEPS', 'AVANT_BRAS', 'ABDOMINAUX',
        'QUADRICEPS', 'ISCHIO_JAMBIERS', 'FESSIERS', 'MOLLETS', 'CORPS_ENTIER'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
