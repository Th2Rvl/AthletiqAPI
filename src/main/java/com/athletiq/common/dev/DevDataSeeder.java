package com.athletiq.common.dev;

import com.athletiq.organisation.InvitationStatus;
import com.athletiq.organisation.Organisation;
import com.athletiq.organisation.OrganisationCoach;
import com.athletiq.organisation.OrganisationCoachRepository;
import com.athletiq.organisation.OrganisationInvitation;
import com.athletiq.organisation.OrganisationInvitationRepository;
import com.athletiq.organisation.OrganisationRepository;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserService;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Données de démonstration, uniquement en profil {@code dev}. Idempotent : un compte est ignoré si son
 * e-mail existe déjà, une organisation si son nom existe déjà.
 *
 * <p>Les organisations sont écrites par les repositories et non par {@code OrganisationService} :
 * au démarrage il n'y a pas d'utilisateur connecté, donc pas d'admin pour passer {@code assertAdmin()}.
 * Ce composant d'infrastructure n'est jamais chargé hors du profil dev.
 */
@Slf4j
@Component
@Profile("dev")
@Order(2)
public class DevDataSeeder implements ApplicationRunner {

    private static final String RENNES = "Stade Rennais Athlétisme";
    private static final String NANTES = "Nantes Métropole Athlétisme";
    private static final String MORBIHAN = "Trail Club du Morbihan";

    private record DemoUser(String email, String firstName, String lastName, Role role, List<String> organisations) {
    }

    private static final List<DemoUser> USERS = List.of(
            new DemoUser("claire.martin@stade-rennais-athle.fr", "Claire", "Martin", Role.COACH, List.of(RENNES, NANTES)),
            new DemoUser("julien.lefebvre@stade-rennais-athle.fr", "Julien", "Lefebvre", Role.COACH, List.of(RENNES)),
            new DemoUser("sophie.bernard@nantes-metropole-athle.fr", "Sophie", "Bernard", Role.COACH, List.of(NANTES)),
            new DemoUser("camille.dubois@nantes-metropole-athle.fr", "Camille", "Dubois", Role.COACH, List.of(NANTES)),
            new DemoUser("thomas.moreau@coach-independant.fr", "Thomas", "Moreau", Role.COACH, List.of()),
            new DemoUser("coach@club.fr", "Nicolas", "Petit", Role.COACH, List.of()),
            new DemoUser("lucas.girard@athlete.fr", "Lucas", "Girard", Role.ATHLETE, List.of()));

    private final UserService userService;
    private final OrganisationRepository organisationRepository;
    private final OrganisationCoachRepository coachRepository;
    private final OrganisationInvitationRepository invitationRepository;
    private final Clock clock;
    private final String demoPassword;

    public DevDataSeeder(UserService userService,
                         OrganisationRepository organisationRepository,
                         OrganisationCoachRepository coachRepository,
                         OrganisationInvitationRepository invitationRepository,
                         Clock clock,
                         @Value("${athletiq.dev.demo-password}") String demoPassword) {
        this.userService = userService;
        this.organisationRepository = organisationRepository;
        this.coachRepository = coachRepository;
        this.invitationRepository = invitationRepository;
        this.clock = clock;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, Organisation> created = new HashMap<>();
        createOrganisation(RENNES, "Club d'athlétisme rennais : sprint, demi-fond et épreuves combinées.", created);
        createOrganisation(NANTES, "Section piste et cross de la métropole nantaise.", created);
        createOrganisation(MORBIHAN, null, created);

        for (DemoUser demo : USERS) {
            if (userService.existsByEmail(demo.email())) {
                continue;
            }
            User user = userService.create(demo.email(), demoPassword, demo.firstName(), demo.lastName(), demo.role());
            for (String organisationName : demo.organisations()) {
                Organisation organisation = created.get(organisationName);
                if (organisation != null) {
                    coachRepository.save(OrganisationCoach.builder()
                            .organisation(organisation)
                            .coach(user)
                            .joinedAt(clock.instant())
                            .build());
                }
            }
        }

        Organisation rennes = created.get(RENNES);
        if (rennes != null) {
            invitationRepository.save(OrganisationInvitation.builder()
                    .organisation(rennes)
                    .email("manon.roux@stade-rennais-athle.fr")
                    .status(InvitationStatus.PENDING)
                    .build());
        }
        log.info("Données de démonstration : {} organisation(s) créée(s)", created.size());
    }

    private void createOrganisation(String name, String description, Map<String, Organisation> created) {
        if (organisationRepository.existsByName(name)) {
            return;
        }
        created.put(name, organisationRepository.save(Organisation.builder()
                .name(name)
                .description(description)
                .build()));
    }
}
