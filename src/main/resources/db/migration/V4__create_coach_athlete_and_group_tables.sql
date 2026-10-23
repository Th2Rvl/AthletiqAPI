-- Lien coach ↔ athlète par code d'invitation, groupes d'athlètes et leur composition (#3).

-- D1/D2 : une invitation par athlète attendu, code à usage unique. Le code n'est jamais stocké en clair :
--   - code_encrypted : code chiffré en AES-256-GCM (version de clé + IV + texte chiffré + tag), pour que
--     le coach puisse réafficher ses codes en attente ;
--   - code_hash : HMAC-SHA256 du code normalisé (clé secrète distincte), seul index de recherche à la saisie.
-- « En attente » = revoked_at IS NULL AND consumed_at IS NULL AND expires_at > maintenant.
CREATE TABLE coach_invitation
(
    id             BINARY(16)    NOT NULL,
    coach_id       BINARY(16)    NOT NULL,
    label          VARCHAR(100)  NOT NULL,
    code_encrypted VARBINARY(64) NOT NULL,
    code_hash      VARCHAR(64)   NOT NULL,
    expires_at     DATETIME(6)   NOT NULL,
    revoked_at     DATETIME(6)   NULL,
    consumed_at    DATETIME(6)   NULL,
    consumed_by_id BINARY(16)    NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    CONSTRAINT pk_coach_invitation PRIMARY KEY (id),
    CONSTRAINT uk_coach_invitation_code_hash UNIQUE (code_hash),
    CONSTRAINT fk_coach_invitation_coach FOREIGN KEY (coach_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_coach_invitation_consumed_by FOREIGN KEY (consumed_by_id)
        REFERENCES app_user (id) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_coach_invitation_coach ON coach_invitation (coach_id, created_at);

-- RM2 : un athlète peut être suivi par plusieurs coachs, mais un couple (coach, athlète) n'a qu'un lien actif.
-- D5 : le retrait clôture le lien (ended_at) pour garder l'historique ; une ré-invitation crée une nouvelle ligne.
-- MySQL n'a pas d'index unique partiel : active_key vaut 1 pour le lien actif et NULL pour un lien clôturé,
-- et un index UNIQUE accepte plusieurs NULL (même technique que pending_email en V2).
CREATE TABLE coach_athlete
(
    id         BINARY(16)  NOT NULL,
    coach_id   BINARY(16)  NOT NULL,
    athlete_id BINARY(16)  NOT NULL,
    joined_at  DATETIME(6) NOT NULL,
    ended_at   DATETIME(6) NULL,
    active_key TINYINT GENERATED ALWAYS AS (CASE WHEN ended_at IS NULL THEN 1 END) STORED,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_coach_athlete PRIMARY KEY (id),
    CONSTRAINT uk_coach_athlete_active UNIQUE (coach_id, athlete_id, active_key),
    CONSTRAINT fk_coach_athlete_coach FOREIGN KEY (coach_id)
        REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_coach_athlete_athlete FOREIGN KEY (athlete_id)
        REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_coach_athlete_athlete ON coach_athlete (athlete_id);

-- RM7 : un groupe appartient à un coach ; nom unique par coach, insensible à la casse et aux accents.
-- La collation est NO PAD : l'API retire les espaces de bord avant d'écrire. « group » est un mot réservé.
CREATE TABLE athlete_group
(
    id         BINARY(16)                             NOT NULL,
    coach_id   BINARY(16)                             NOT NULL,
    name       VARCHAR(60) COLLATE utf8mb4_0900_ai_ci NOT NULL,
    created_at DATETIME(6)                            NOT NULL,
    updated_at DATETIME(6)                            NOT NULL,
    CONSTRAINT pk_athlete_group PRIMARY KEY (id),
    CONSTRAINT uk_athlete_group_name UNIQUE (coach_id, name),
    CONSTRAINT fk_athlete_group_coach FOREIGN KEY (coach_id)
        REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- RM8 : un athlète est au plus une fois dans un groupe. RM9 : supprimer un groupe supprime ses appartenances,
-- jamais les athlètes ni leurs liens avec le coach.
CREATE TABLE athlete_group_member
(
    id         BINARY(16)  NOT NULL,
    group_id   BINARY(16)  NOT NULL,
    athlete_id BINARY(16)  NOT NULL,
    added_at   DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_athlete_group_member PRIMARY KEY (id),
    CONSTRAINT uk_athlete_group_member UNIQUE (group_id, athlete_id),
    CONSTRAINT fk_athlete_group_member_group FOREIGN KEY (group_id)
        REFERENCES athlete_group (id) ON DELETE CASCADE,
    CONSTRAINT fk_athlete_group_member_athlete FOREIGN KEY (athlete_id)
        REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_athlete_group_member_athlete ON athlete_group_member (athlete_id);
