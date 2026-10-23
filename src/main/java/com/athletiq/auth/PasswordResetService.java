package com.athletiq.auth;

import com.athletiq.auth.dto.ForgotPasswordRequest;
import com.athletiq.auth.dto.ForgotPasswordResponse;
import com.athletiq.auth.dto.ResetPasswordRequest;
import com.athletiq.user.User;
import com.athletiq.user.UserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Réinitialisation du mot de passe (US08).
 *
 * <p>Le jeton brut est tiré au hasard, transmis au demandeur et <strong>seul son empreinte
 * SHA-256</strong> est conservée : une fuite de la base ne permet pas de réinitialiser un mot
 * de passe. Le jeton est à usage unique et expire (RM6).
 *
 * <p>La demande de réinitialisation répond toujours de la même façon, que le compte existe ou
 * non (RM5) : la branche « compte trouvé » est la seule qui crée un jeton, mais la réponse HTTP
 * est identique dans les deux cas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserService userService;
    private final ResetTokenProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /**
     * Traite une demande de réinitialisation. La réponse ne dépend pas de l'existence du compte.
     */
    @Transactional
    public ForgotPasswordResponse requestReset(ForgotPasswordRequest request) {
        Optional<User> user = userService.findByEmail(request.email());
        if (user.isEmpty()) {
            log.info("Demande de réinitialisation pour un e-mail inconnu : aucune réponse différente");
            return ForgotPasswordResponse.accepted(null);
        }
        User account = user.get();
        revokePendingTokens(account.getId());
        String token = generateToken();
        tokenRepository.save(PasswordResetToken.builder()
                .user(account)
                .tokenHash(hash(token))
                .expiresAt(clock.instant().plus(properties.ttl()))
                .build());
        log.info("Jeton de réinitialisation créé pour le compte {}", account.getId());
        return ForgotPasswordResponse.accepted(properties.exposesToken() ? token : null);
    }

    /**
     * Change le mot de passe à partir d'un jeton. Un jeton absent, inconnu, expiré ou déjà
     * utilisé est refusé par le même code : rien n'est révélé sur sa raison réelle.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.password().equals(request.passwordConfirmation())) {
            throw AuthErrors.passwordConfirmationMismatch();
        }
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(hash(request.token()))
                .orElseThrow(AuthErrors::resetTokenInvalid);
        Instant now = clock.instant();
        if (!resetToken.isUsableAt(now)) {
            throw AuthErrors.resetTokenInvalid();
        }
        User account = resetToken.getUser();
        userService.updatePassword(account.getId(), request.password());
        // Le jeton est consommé : il ne pourra plus servir, et les autres jetons du compte tombent.
        resetToken.setUsedAt(now);
        tokenRepository.save(resetToken);
        revokePendingTokens(account.getId());
        log.info("Mot de passe réinitialisé pour le compte {}", account.getId());
    }

    /** Invalide les jetons encore valides d'un compte. */
    private void revokePendingTokens(UUID userId) {
        List<PasswordResetToken> pending = tokenRepository.findByUserIdAndUsedAtIsNull(userId);
        if (pending.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        pending.forEach(token -> token.setUsedAt(now));
        tokenRepository.saveAll(pending);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Empreinte SHA-256 du jeton, en hexadécimal minuscule : c'est ce qui est stocké. */
    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 est requis par la plateforme", impossible);
        }
    }
}
