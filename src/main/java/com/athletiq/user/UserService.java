package com.athletiq.user;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accès aux comptes pour les autres fonctionnalités (auth, organisation…), qui ne touchent jamais
 * directement au {@link UserRepository}.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(EmailNormalizer.normalize(email));
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(EmailNormalizer.normalize(email));
    }

    /** Crée un compte. Le mot de passe en clair est hashé en BCrypt et n'est jamais conservé. */
    @Transactional
    public User create(String email, String rawPassword, String firstName, String lastName, Role role) {
        User user = User.builder()
                .email(EmailNormalizer.normalize(email))
                .passwordHash(passwordEncoder.encode(rawPassword))
                .firstName(firstName)
                .lastName(lastName)
                .role(role)
                .build();
        return userRepository.save(user);
    }
}
