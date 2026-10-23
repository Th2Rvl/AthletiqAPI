package com.athletiq.auth;

import java.util.regex.Pattern;

/**
 * Règle de robustesse des mots de passe (RM2) : au moins 12 caractères, une lettre et un chiffre.
 *
 * <p>Elle est appliquée côté API, qui est la source de vérité : le web aligne son message sur
 * cette règle, mais ne peut pas s'y substituer.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 72;

    /** 72 est la taille maximale d'un mot de passe BCrypt, et au-delà le hachage est tronqué. */
    private static final Pattern LETTER = Pattern.compile(".*\\p{L}.*", Pattern.DOTALL);
    private static final Pattern DIGIT = Pattern.compile(".*\\d.*", Pattern.DOTALL);

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        return password != null
                && password.length() >= MIN_LENGTH
                && password.length() <= MAX_LENGTH
                && LETTER.matcher(password).matches()
                && DIGIT.matcher(password).matches();
    }
}
