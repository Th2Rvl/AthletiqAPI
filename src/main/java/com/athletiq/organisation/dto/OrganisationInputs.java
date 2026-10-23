package com.athletiq.organisation.dto;

/** Normalisation partagée par les requêtes de création et de modification. */
final class OrganisationInputs {

    private OrganisationInputs() {
    }

    static String normalizeName(String name) {
        return name == null ? null : name.strip();
    }

    static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String stripped = description.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
