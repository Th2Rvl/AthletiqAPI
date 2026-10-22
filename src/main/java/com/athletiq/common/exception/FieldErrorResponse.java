package com.athletiq.common.exception;

/** Une erreur de validation : le nom JSON du champ et le message. La valeur rejetée n'est jamais renvoyée. */
public record FieldErrorResponse(String field, String message) {
}
