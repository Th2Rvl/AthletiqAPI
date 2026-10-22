-- Organisations (clubs, structures), rattachements des coachs et invitations.

-- RM1 : nom unique, insensible à la casse et aux accents grâce à la collation utf8mb4_0900_ai_ci.
-- Cette collation est NO PAD : les espaces de fin comptent, c'est pourquoi l'API retire les espaces avant d'écrire.
CREATE TABLE organisation
(
    id          BINARY(16)                                NOT NULL,
    name        VARCHAR(100) COLLATE utf8mb4_0900_ai_ci   NOT NULL,
    description VARCHAR(500)                              NULL,
    created_at  DATETIME(6)                               NOT NULL,
    updated_at  DATETIME(6)                               NOT NULL,
    CONSTRAINT pk_organisation PRIMARY KEY (id),
    CONSTRAINT uk_organisation_name UNIQUE (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- RM3 : un coach n'est membre qu'une fois d'une même organisation.
-- RM7 : la suppression d'une organisation supprime ses rattachements, jamais les comptes.
CREATE TABLE organisation_coach
(
    id              BINARY(16)  NOT NULL,
    organisation_id BINARY(16)  NOT NULL,
    coach_id        BINARY(16)  NOT NULL,
    joined_at       DATETIME(6) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    CONSTRAINT pk_organisation_coach PRIMARY KEY (id),
    CONSTRAINT uk_organisation_coach UNIQUE (organisation_id, coach_id),
    CONSTRAINT fk_organisation_coach_organisation FOREIGN KEY (organisation_id)
        REFERENCES organisation (id) ON DELETE CASCADE,
    CONSTRAINT fk_organisation_coach_coach FOREIGN KEY (coach_id)
        REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_organisation_coach_coach ON organisation_coach (coach_id);

-- RM4 : au plus une invitation PENDING par (organisation, e-mail). MySQL n'a pas d'index unique partiel :
-- la colonne générée pending_email vaut l'e-mail si l'invitation est PENDING, NULL sinon, et un index
-- UNIQUE accepte plusieurs NULL. Les invitations ACCEPTED et CANCELLED ne se gênent donc pas.
CREATE TABLE organisation_invitation
(
    id              BINARY(16)   NOT NULL,
    organisation_id BINARY(16)   NOT NULL,
    email           VARCHAR(254) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    coach_id        BINARY(16)   NULL,
    pending_email   VARCHAR(254) GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN email END) STORED,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_organisation_invitation PRIMARY KEY (id),
    CONSTRAINT uk_organisation_invitation_pending UNIQUE (organisation_id, pending_email),
    CONSTRAINT ck_organisation_invitation_status CHECK (status IN ('PENDING', 'ACCEPTED', 'CANCELLED')),
    CONSTRAINT fk_organisation_invitation_organisation FOREIGN KEY (organisation_id)
        REFERENCES organisation (id) ON DELETE CASCADE,
    CONSTRAINT fk_organisation_invitation_coach FOREIGN KEY (coach_id)
        REFERENCES app_user (id) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_organisation_invitation_email_status ON organisation_invitation (email, status);
