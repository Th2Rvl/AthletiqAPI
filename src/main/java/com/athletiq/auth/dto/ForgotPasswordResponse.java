package com.athletiq.auth.dto;

/**
 * Réponse à une demande de réinitialisation.
 *
 * <p>Le message et le code HTTP sont identiques que le compte existe ou non (RM5) : la
 * réponse ne révèle rien. {@code resetToken} ne porte le jeton brut que si
 * {@code athletiq.security.reset-token-exposed} est actif, c'est-à-dire hors production.
 */
public record ForgotPasswordResponse(boolean accepted, String message, String resetToken) {

    /** Message identique dans tous les cas, pour ne pas révéler l'existence d'un compte. */
    public static final String MESSAGE = "Si un compte existe pour cet e-mail, un lien de réinitialisation a été envoyé.";

    public static ForgotPasswordResponse accepted(String resetToken) {
        return new ForgotPasswordResponse(true, MESSAGE, resetToken);
    }
}
