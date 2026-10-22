package com.athletiq.auth;

import com.athletiq.common.exception.BusinessRuleException;
import com.athletiq.common.exception.InvalidFieldException;

/**
 * Codes et messages d'erreur de l'authentification et du compte coach. Le front s'appuie sur
 * les codes : ils ne changent pas quand le libellé s'améliore.
 */
final class AuthErrors {

    static final String EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS";
    static final String PASSWORD_RULE_VIOLATION = "PASSWORD_RULE_VIOLATION";
    static final String RESET_TOKEN_INVALID = "RESET_TOKEN_INVALID";
    static final String PASSWORD_CONFIRMATION_MISMATCH = "PASSWORD_CONFIRMATION_MISMATCH";

    private AuthErrors() {
    }

    static BusinessRuleException emailAlreadyExists() {
        return new BusinessRuleException(EMAIL_ALREADY_EXISTS, "Un compte existe déjà avec cet e-mail", "email");
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
