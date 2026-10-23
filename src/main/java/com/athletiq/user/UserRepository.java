package com.athletiq.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Vrai si l'e-mail est porté par un autre compte que celui-ci : sert à la modification du profil. */
    boolean existsByEmailAndIdNot(String email, UUID id);
}
