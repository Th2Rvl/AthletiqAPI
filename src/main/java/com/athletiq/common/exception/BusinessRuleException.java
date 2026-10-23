package com.athletiq.common.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Règle métier non respectée : 409. */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(String code, String detail) {
        this(code, detail, null);
    }

    public BusinessRuleException(String code, String detail, String field) {
        super(code, detail, field);
    }

    /** Avec des propriétés supplémentaires recopiées dans le ProblemDetail (ex. {@code workoutCount}). */
    public BusinessRuleException(String code, String detail, String field, Map<String, Object> properties) {
        super(code, detail, field, properties);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
