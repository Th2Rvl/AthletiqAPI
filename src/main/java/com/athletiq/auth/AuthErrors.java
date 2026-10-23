package com.athletiq.auth;

import com.athletiq.common.exception.InvalidFieldException;

/**
 * Codes et messages d'erreur propres à l'authentification. Ceux qui concernent le compte
 * lui-même (e-mail déjà pris) sont dans {@link com.athletiq.user.UserErrors}.
 */
final class AuthErrors {

    static final String PASSWORD_RULE_VIOLATION = "PASSWORD_RULE_VIOLATION";
    static final String RESET_TOKEN_INVALID = "RESET_TOKEN_INVALID";
    static final String PASSWORD_CONFIRMATION_MISMATCH = "PASSWORD_CONFIRMATION_MISMATCH";

    private AuthErrors() {
    }

    static InvalidFieldException passwordRuleViolation() {
        return new InvalidFieldException(PASSWORD_RULE_VIOLATION,
                "Le mot de passe doit contenir au moins 12 caractères, une lettre et un chiffre", "password");
    }

    /** Un seul code pour un jeton absent, inconnu, expiré ou déjà utilisé : rien n'est révélé. */
    static InvalidFieldException resetTokenInvalid() {
        return new InvalidFieldException(RESET_TOKEN_INVALID, "Ce lien de réinitialisation n'est plus valable", "token");
    }

    static InvalidFieldException passwordConfirmationMismatch() {
        return new InvalidFieldException(PASSWORD_CONFIRMATION_MISMATCH, "Les deux mots de passe ne correspondent pas",
                "passwordConfirmation");
    }
}
