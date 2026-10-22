package com.athletiq.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Racine des exceptions métier. Chacune porte un {@code code} stable (le front s'appuie dessus),
 * un {@code detail} lisible et, au besoin, le champ concerné. Le {@link GlobalExceptionHandler}
 * les traduit en {@code ProblemDetail}.
 */
@Getter
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final String detail;
    private final String field;

    protected DomainException(String code, String detail, String field) {
        super(detail);
        this.code = code;
        this.detail = detail;
        this.field = field;
    }

    public abstract HttpStatus getStatus();
}
