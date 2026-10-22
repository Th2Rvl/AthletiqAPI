package com.athletiq.common.exception;

import org.springframework.http.HttpStatus;

/** Paramètre de requête refusé par le service (ex. propriété de tri non autorisée) : 400. */
public class InvalidParameterException extends DomainException {

    public InvalidParameterException(String parameterName) {
        super(ErrorCodes.INVALID_PARAMETER, "Paramètre invalide : " + parameterName, null);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
