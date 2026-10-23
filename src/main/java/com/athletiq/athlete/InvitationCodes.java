package com.athletiq.athlete;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;

/**
 * Format des codes d'invitation (D3, D4) : 8 caractères tirés par {@link SecureRandom} dans
 * l'alphabet du contrat, sans les caractères ambigus {@code 0 O 1 I L}. Cet alphabet compte 31
 * caractères (23 lettres et 8 chiffres), soit environ 39,6 bits d'aléa par code.
 */
public final class InvitationCodes {

    public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private InvitationCodes() {
    }

    /** Un nouveau code, sans tiret (le front l'affiche en {@code XXXX-XXXX}). */
    public static String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * Normalise une saisie : espaces de bord, tirets et espaces internes retirés, majuscules.
     * Vide si le résultat n'a pas le format d'un code : l'appelant renvoie alors la même erreur
     * qu'un code inconnu, pour ne rien distinguer.
     */
    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String normalized = input.strip().replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        if (normalized.length() != LENGTH) {
            return Optional.empty();
        }
        for (int i = 0; i < normalized.length(); i++) {
            if (ALPHABET.indexOf(normalized.charAt(i)) < 0) {
                return Optional.empty();
            }
        }
        return Optional.of(normalized);
    }
}
