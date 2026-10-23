package com.athletiq.common.exception;

import org.springframework.http.HttpStatus;

/** Ressource absente, ou invisible pour l'utilisateur courant : 404. */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String code, String detail) {
        super(code, detail, null);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
