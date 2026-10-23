package com.athletiq.user;

import java.util.Optional;
import java.util.UUID;
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
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(EmailNormalizer.normalize(email));
    }

    /** Vrai si l'e-mail est déjà porté par un compte autre que celui-ci. */
    @Transactional(readOnly = true)
    public boolean existsByEmailOtherThan(String email, UUID userId) {
        return userRepository.existsByEmailAndIdNot(EmailNormalizer.normalize(email), userId);
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

    /**
     * Change le mot de passe d'un compte. Le mot de passe en clair n'est jamais conservé : seul son
     * hash BCrypt l'est.
     */
    @Transactional
    public void updatePassword(UUID userId, String rawPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Compte introuvable : " + userId));
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }

    /**
     * Met à jour les informations du compte. Ne touche ni au rôle ni au mot de passe : le rôle
     * est fixé à la création (RM3) et le mot de passe se change par son propre parcours.
     */
    @Transactional
    public User updateProfile(UUID userId, String email, String firstName, String lastName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Compte introuvable : " + userId));
        if (email != null && existsByEmailOtherThan(email, userId)) {
            throw UserErrors.emailAlreadyExists();
        }
        if (email != null) {
            user.setEmail(EmailNormalizer.normalize(email));
        }
        if (firstName != null) {
            user.setFirstName(firstName.strip());
        }
        if (lastName != null) {
            user.setLastName(lastName.strip());
        }
        return userRepository.save(user);
    }
}
