-- Jetons de réinitialisation du mot de passe. Seul l'empreinte SHA-256 du jeton est stockée :
-- une fuite de la table ne permet pas de réinitialiser le mot de passe d'un compte.
CREATE TABLE password_reset_token
(
    id         BINARY(16)  NOT NULL,
    user_id    BINARY(16)  NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_password_reset_token PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_token_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_password_reset_token_user ON password_reset_token (user_id);
