package com.athletiq.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Jetons encore valides d'un compte : ils sont révoqués à chaque nouvelle demande. */
    List<PasswordResetToken> findByUserIdAndUsedAtIsNull(UUID userId);
}
