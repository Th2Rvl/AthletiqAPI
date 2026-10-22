package com.athletiq.organisation;

import com.athletiq.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Club ou structure qui utilise Athletiq. Volontairement sans collection {@code @OneToMany} :
 * les coachs et les invitations se chargent par requête, page par page.
 */
@Entity
@Table(name = "organisation")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Organisation extends BaseEntity {

    /** Unique, insensible à la casse et aux accents (collation utf8mb4_0900_ai_ci). */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;
}
