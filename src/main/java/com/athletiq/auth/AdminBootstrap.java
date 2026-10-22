package com.athletiq.auth;

import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Crée le premier administrateur au démarrage, à partir des variables d'environnement.
 * Ne remplace jamais un compte existant (ni son mot de passe, ni son rôle) et ne logge jamais le mot de passe.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
@EnableConfigurationProperties(AdminProperties.class)
public class AdminBootstrap implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;

    private final AdminProperties properties;
    private final UserService userService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            log.warn("Aucun admin bootstrap configuré (ATHLETIQ_ADMIN_EMAIL / ATHLETIQ_ADMIN_PASSWORD)");
            return;
        }
        if (properties.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "ATHLETIQ_ADMIN_PASSWORD doit contenir au moins " + MIN_PASSWORD_LENGTH + " caractères");
        }
        Optional<User> existing = userService.findByEmail(properties.email());
        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.ADMIN) {
                log.error("Le compte admin bootstrap existe déjà avec le rôle {} : il n'est pas modifié",
                        existing.get().getRole());
            }
            return;
        }
        userService.create(properties.email(), properties.password(), "Admin", "Athletiq", Role.ADMIN);
        log.info("Compte admin bootstrap créé");
    }
}
