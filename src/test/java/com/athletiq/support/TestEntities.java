package com.athletiq.support;

import com.athletiq.organisation.InvitationStatus;
import com.athletiq.organisation.Organisation;
import com.athletiq.organisation.OrganisationCoach;
import com.athletiq.organisation.OrganisationInvitation;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import java.time.Instant;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/** Entités prêtes à l'emploi pour les tests unitaires (id renseigné comme après une sauvegarde). */
public final class TestEntities {

    private TestEntities() {
    }

    public static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    public static User user(String email, Role role) {
        return withId(User.builder()
                .email(email)
                .passwordHash("$2a$10$hash")
                .firstName("Claire")
                .lastName("Martin")
                .role(role)
                .build(), UUID.randomUUID());
    }

    public static Organisation organisation(String name) {
        return withId(Organisation.builder().name(name).build(), UUID.randomUUID());
    }

    public static OrganisationCoach membership(Organisation organisation, User coach) {
        return withId(OrganisationCoach.builder()
                .organisation(organisation)
                .coach(coach)
                .joinedAt(Instant.parse("2026-10-01T08:00:00Z"))
                .build(), UUID.randomUUID());
    }

    public static OrganisationInvitation invitation(Organisation organisation, String email, InvitationStatus status) {
        return withId(OrganisationInvitation.builder()
                .organisation(organisation)
                .email(email)
                .status(status)
                .build(), UUID.randomUUID());
    }
}
