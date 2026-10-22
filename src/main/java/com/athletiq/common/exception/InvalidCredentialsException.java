package com.athletiq.common.exception;

import org.springframework.http.HttpStatus;

/** Identifiants de connexion refusés : 401, sans dire si c'est l'e-mail ou le mot de passe. */
public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super(ErrorCodes.INVALID_CREDENTIALS, "E-mail ou mot de passe incorrect", null);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNAUTHORIZED;
    }
}
