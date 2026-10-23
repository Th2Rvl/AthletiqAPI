package com.athletiq.athlete;

import com.athletiq.athlete.dto.GroupRef;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Groupes des athlètes d'un coach, pour la colonne « Groupes » de la liste. L'interface appartient
 * à {@code athlete} et est implémentée par {@code group} : ainsi {@code group} dépend de
 * {@code athlete}, jamais l'inverse.
 */
public interface AthleteGroupDirectory {

    /**
     * Groupes de ce coach dont chaque athlète est membre, triés par nom, en une seule requête.
     * Un athlète sans groupe est absent de la map.
     */
    Map<UUID, List<GroupRef>> groupsByAthlete(UUID coachId, Collection<UUID> athleteIds);
}
