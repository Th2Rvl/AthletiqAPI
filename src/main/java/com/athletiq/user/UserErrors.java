package com.athletiq.user;

import com.athletiq.common.exception.BusinessRuleException;

/**
 * Codes et messages d'erreur de la fonctionnalité compte. Le front s'appuie sur les codes :
 * ils ne changent pas quand le libellé s'améliore.
 */
public final class UserErrors {

    public static final String EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS";

    private UserErrors() {
    }

    /** Un e-mail est déjà porté par un autre compte (RM1). */
    public static BusinessRuleException emailAlreadyExists() {
        return new BusinessRuleException(EMAIL_ALREADY_EXISTS, "Un compte existe déjà avec cet e-mail", "email");
    }
}
