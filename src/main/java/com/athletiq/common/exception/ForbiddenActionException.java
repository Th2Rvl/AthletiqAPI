package com.athletiq.common.exception;

import org.springframework.http.HttpStatus;

/** Action interdite à l'utilisateur courant : 403. */
public class ForbiddenActionException extends DomainException {

    public ForbiddenActionException(String code, String detail) {
        super(code, detail, null);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.FORBIDDEN;
    }
}
