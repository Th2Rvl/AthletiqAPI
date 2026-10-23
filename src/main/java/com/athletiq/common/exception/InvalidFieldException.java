package com.athletiq.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Valeur de champ refusée par une règle métier : 400, avec le champ concerné.
 * Complète {@link BusinessRuleException} (409), qui exprime un conflit d'état avec le reste
 * des données, et non une valeur mal formée.
 */
public class InvalidFieldException extends DomainException {

    public InvalidFieldException(String code, String detail, String field) {
        super(code, detail, field);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
