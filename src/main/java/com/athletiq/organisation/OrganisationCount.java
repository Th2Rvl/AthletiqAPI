package com.athletiq.organisation;

import java.util.UUID;

/** Projection d'un comptage groupé par organisation (calcul des compteurs d'une page sans N+1). */
public record OrganisationCount(UUID organisationId, long total) {
}
