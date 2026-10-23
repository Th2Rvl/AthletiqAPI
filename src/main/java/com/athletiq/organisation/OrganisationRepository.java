package com.athletiq.organisation;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganisationRepository extends JpaRepository<Organisation, UUID> {

    /** La comparaison se fait côté MySQL avec la collation ai_ci : insensible à la casse et aux accents. */
    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    /** {@code LIKE %q%} (Spring Data échappe {@code %} et {@code _}), insensible casse et accents par la collation. */
    Page<Organisation> findByNameContaining(String name, Pageable pageable);
}
