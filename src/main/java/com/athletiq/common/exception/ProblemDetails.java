package com.athletiq.common.exception;

import java.net.URI;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Fabrique des ProblemDetail du projet : toujours un {@code code}, et l'URI appelée dans {@code instance}. */
public final class ProblemDetails {

    public static final String CODE = "code";
    public static final String FIELD = "field";
    public static final String ERRORS = "errors";

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE, code);
        if (path != null) {
            problem.setInstance(URI.create(path));
        }
        return problem;
    }
}
