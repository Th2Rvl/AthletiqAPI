package com.athletiq.common.exception;

import org.springframework.http.HttpStatus;

/** Règle métier non respectée : 409. */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(String code, String detail) {
        this(code, detail, null);
    }

    public BusinessRuleException(String code, String detail, String field) {
        super(code, detail, field);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
