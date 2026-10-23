package com.athletiq.group;

import com.athletiq.common.persistence.BaseEntity;
import com.athletiq.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Groupe d'athlètes d'un coach (RM7). Table {@code athlete_group} : {@code group} est un mot
 * réservé SQL. Le nom est unique par coach, sans tenir compte de la casse ni des accents (collation
 * de la colonne).
 */
@Entity
@Table(name = "athlete_group")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AthleteGroup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private User coach;

    /** Toujours sans espaces de bord : la collation est NO PAD. */
    @Column(name = "name", nullable = false, length = 60)
    private String name;
}
