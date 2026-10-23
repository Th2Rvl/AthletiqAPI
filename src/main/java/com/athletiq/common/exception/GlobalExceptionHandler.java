package com.athletiq.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Point unique de traduction des exceptions en réponses {@code ProblemDetail} (RFC 9457).
 * Chaque réponse porte un {@code code} stable. Les stack traces ne sont jamais renvoyées,
 * seulement loggées pour les erreurs imprévues.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomain(DomainException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetails.of(ex.getStatus(), ex.getCode(), ex.getDetail(), request.getRequestURI());
        if (ex.getField() != null) {
            problem.setProperty(ProblemDetails.FIELD, ex.getField());
        }
        ex.getProperties().forEach(problem::setProperty);
        return ResponseEntity.status(ex.getStatus()).body(problem);
    }

    /** Refus d'un {@code @PreAuthorize} : sans ce handler, il finirait en 500. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, ErrorCodes.ACCESS_DENIED, "Accès refusé", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHENTICATED, "Authentification requise", request);
    }

    /** Filet de sécurité : une contrainte de base que le service n'a pas traduite en erreur métier. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        // Le message du driver contient la valeur en conflit (ex. un e-mail) : on ne logge que la contrainte.
        log.warn("Contrainte de base de données non traduite sur {} : {}", request.getRequestURI(),
                constraintName(ex));
        return problem(HttpStatus.CONFLICT, ErrorCodes.DATA_CONFLICT, "Conflit de données", request);
    }

    private static String constraintName(DataIntegrityViolationException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return violation.getConstraintName();
            }
        }
        return "inconnue";
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Erreur inattendue sur {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "Erreur interne", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        ProblemDetail problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED,
                "La requête contient des champs invalides", path(request));
        problem.setProperty(ProblemDetails.ERRORS, fieldErrors(ex.getFieldErrors()));
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(@NonNull HttpMessageNotReadableException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        ProblemDetail problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, ErrorCodes.MALFORMED_REQUEST,
                "Corps de requête illisible", path(request));
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    /** UUID mal formé dans le chemin, valeur d'enum inconnue en paramètre… */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(@NonNull TypeMismatchException ex,
                                                        @NonNull HttpHeaders headers,
                                                        @NonNull HttpStatusCode status,
                                                        @NonNull WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : ex.getPropertyName();
        return invalidParameter(name, headers, request);
    }

    /** Contrainte Bean Validation posée sur un {@code @RequestParam} (ex. {@code q} trop long). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            @NonNull HandlerMethodValidationException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        String name = ex.getParameterValidationResults().stream()
                .map(result -> result.getMethodParameter().getParameterName())
                .findFirst()
                .orElse("requête");
        return invalidParameter(name, headers, request);
    }

    /**
     * Toutes les autres exceptions MVC gérées par la classe parente (405, 415, paramètre manquant…)
     * passent par ici : on garantit la présence de {@code code} et de {@code instance}.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(@NonNull Exception ex, Object body,
                                                             @NonNull HttpHeaders headers,
                                                             @NonNull HttpStatusCode statusCode,
                                                             @NonNull WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            if (problem.getProperties() == null || !problem.getProperties().containsKey(ProblemDetails.CODE)) {
                HttpStatus resolved = HttpStatus.resolve(statusCode.value());
                problem.setProperty(ProblemDetails.CODE, resolved != null ? resolved.name() : "ERROR");
            }
            if (problem.getInstance() == null && path(request) != null) {
                problem.setInstance(URI.create(path(request)));
            }
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private ResponseEntity<Object> invalidParameter(String name, HttpHeaders headers, WebRequest request) {
        ProblemDetail problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_PARAMETER,
                "Paramètre invalide : " + name, path(request));
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    /**
     * Une seule erreur par champ, pour que le front affiche un message clair :
     * « obligatoire » l'emporte sur les autres contraintes (un nom vide viole aussi la longueur minimale).
     */
    private static List<FieldErrorResponse> fieldErrors(List<FieldError> errors) {
        Map<String, FieldError> byField = new LinkedHashMap<>();
        for (FieldError error : errors) {
            byField.merge(error.getField(), error,
                    (kept, candidate) -> "NotBlank".equals(candidate.getCode()) ? candidate : kept);
        }
        List<FieldErrorResponse> result = new ArrayList<>();
        byField.forEach((field, error) -> result.add(new FieldErrorResponse(field, error.getDefaultMessage())));
        return result;
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail,
                                                         HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ProblemDetails.of(status, code, detail, request.getRequestURI()));
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : null;
    }
}
