package com.athletiq.athlete;

import com.athletiq.athlete.dto.CoachLinkResponse;
import com.athletiq.athlete.dto.CreateInvitationRequest;
import com.athletiq.athlete.dto.InvitationResponse;
import com.athletiq.athlete.dto.RedeemInvitationRequest;
import com.athletiq.common.security.AccessGuard;
import com.athletiq.common.security.CurrentUser;
import com.athletiq.user.Role;
import com.athletiq.user.User;
import com.athletiq.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invitations d'athlètes (US10) : un code à usage unique par athlète attendu (D1), créé par un
 * coach et saisi par un athlète, ce qui crée le lien de suivi (RM1). Le code n'est jamais stocké
 * ni journalisé en clair (D2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationService {

    static final int MAX_GENERATION_ATTEMPTS = 5;
    static final String UK_COACH_ATHLETE_ACTIVE = "uk_coach_athlete_active";

    private final CoachInvitationRepository invitationRepository;
    private final CoachAthleteRepository linkRepository;
    private final InvitationCodeCipher cipher;
    private final InvitationProperties properties;
    private final AthleteMapper mapper;
    private final UserService userService;
    private final AccessGuard accessGuard;
    private final CurrentUser currentUser;
    private final Clock clock;

    // ---------------------------------------------------------------- côté coach

    /**
     * Crée une invitation et son code. Le code est tiré au hasard ; en cas de collision avec un code
     * existant (même empreinte), un autre est tiré, jusqu'à {@value #MAX_GENERATION_ATTEMPTS} fois.
     */
    @Transactional
    public InvitationResponse create(CreateInvitationRequest request) {
        accessGuard.assertCoach();
        UUID coachId = currentUser.id();
        String code = null;
        String hash = null;
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS && code == null; attempt++) {
            String candidate = InvitationCodes.generate();
            String candidateHash = cipher.hash(candidate);
            if (!invitationRepository.existsByCodeHash(candidateHash)) {
                code = candidate;
                hash = candidateHash;
            }
        }
        if (code == null) {
            throw new IllegalStateException("Impossible de générer un code d'invitation unique");
        }
        Instant now = clock.instant();
        CoachInvitation invitation = CoachInvitation.builder()
                .coach(userService.getReference(coachId))
                .label(request.label())
                .codeEncrypted(cipher.encrypt(code, coachId))
                .codeHash(hash)
                .expiresAt(now.plus(properties.ttl()))
                .build();
        return mapper.toInvitationResponse(invitationRepository.saveAndFlush(invitation), code);
    }

    /** Invitations en attente du coach connecté, de la plus récente à la plus ancienne. */
    @Transactional(readOnly = true)
    public Page<InvitationResponse> listPending(Pageable pageable) {
        accessGuard.assertCoach();
        UUID coachId = currentUser.id();
        return invitationRepository.findPendingByCoachId(coachId, clock.instant(), unsorted(pageable))
                .map(invitation -> mapper.toInvitationResponse(invitation,
                        cipher.decrypt(invitation.getCodeEncrypted(), coachId)));
    }

    /** Révoque une invitation en attente : son code n'est plus utilisable. */
    @Transactional
    public void revoke(UUID invitationId) {
        accessGuard.assertInvitationOwner(invitationId);
        Instant now = clock.instant();
        CoachInvitation invitation = invitationRepository
                .findPendingByIdAndCoachId(invitationId, currentUser.id(), now)
                .orElseThrow(AthleteErrors::invitationNotFound);
        invitation.setRevokedAt(now);
    }

    // ---------------------------------------------------------------- côté athlète

    /**
     * Saisie d'un code par l'athlète connecté (P5). Dans l'ordre :
     * <ol>
     *   <li>forme invalide, code inconnu, expiré, révoqué ou consommé : {@code INVITATION_CODE_INVALID},
     *       sans distinguer les cas ;</li>
     *   <li>athlète déjà suivi par ce coach : {@code ATHLETE_ALREADY_LINKED}, et le code n'est pas
     *       consommé ;</li>
     *   <li>consommation par un UPDATE conditionnel atomique, puis création du lien, dans la même
     *       transaction : si le lien échoue, la consommation est annulée.</li>
     * </ol>
     */
    @Transactional
    public CoachLinkResponse redeem(RedeemInvitationRequest request) {
        accessGuard.assertAthlete();
        UUID athleteId = currentUser.id();
        String code = InvitationCodes.normalize(request.code()).orElseThrow(AthleteErrors::invitationCodeInvalid);
        String hash = cipher.hash(code);
        Instant now = clock.instant();

        CoachInvitation invitation = invitationRepository.findByCodeHash(hash)
                .filter(candidate -> candidate.isPendingAt(now))
                .filter(candidate -> candidate.getCoach().getRole() == Role.COACH)
                .orElseThrow(AthleteErrors::invitationCodeInvalid);
        User coach = invitation.getCoach();
        if (linkRepository.existsByCoachIdAndAthleteIdAndEndedAtIsNull(coach.getId(), athleteId)) {
            throw AthleteErrors.athleteAlreadyLinked();
        }

        User athlete = userService.getReference(athleteId);
        if (invitationRepository.consume(hash, athlete, now) == 0) {
            throw AthleteErrors.invitationCodeInvalid();
        }
        CoachAthlete link = CoachAthlete.builder()
                .coach(coach)
                .athlete(athlete)
                .joinedAt(now)
                .build();
        try {
            linkRepository.saveAndFlush(link);
        } catch (DataIntegrityViolationException ex) {
            if (violates(ex, UK_COACH_ATHLETE_ACTIVE)) {
                // Deux codes du même coach saisis en même temps : l'exception annule aussi la consommation.
                throw AthleteErrors.athleteAlreadyLinked();
            }
            throw ex;
        }
        log.info("Athlète {} rattaché au coach {}", athleteId, coach.getId());
        return mapper.toCoachLinkResponse(link);
    }

    // ---------------------------------------------------------------- interne

    /** Tri fixe : le paramètre {@code sort} du client est ignoré (D6). */
    private static Pageable unsorted(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    private static boolean violates(DataIntegrityViolationException ex, String constraintName) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(constraintName);
    }
}
