package com.athletiq.common.exception;

import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Racine des exceptions métier. Chacune porte un {@code code} stable (le front s'appuie dessus),
 * un {@code detail} lisible et, au besoin, le champ concerné et des propriétés supplémentaires
 * (ex. {@code workoutCount}). Le {@link GlobalExceptionHandler} les traduit en {@code ProblemDetail}.
 */
@Getter
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final String detail;
    private final String field;
    /** Propriétés recopiées telles quelles dans le ProblemDetail ; vide par défaut. */
    private final Map<String, Object> properties;

    protected DomainException(String code, String detail, String field) {
        this(code, detail, field, Map.of());
    }

    protected DomainException(String code, String detail, String field, Map<String, Object> properties) {
        super(detail);
        this.code = code;
        this.detail = detail;
        this.field = field;
        this.properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public abstract HttpStatus getStatus();
}
