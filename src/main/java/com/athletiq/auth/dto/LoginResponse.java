package com.athletiq.auth.dto;

import com.athletiq.user.dto.UserResponse;

/** Réponse de login : access token Bearer, sa durée de vie en secondes et l'utilisateur connecté. */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {

    public static final String BEARER = "Bearer";

    public static LoginResponse bearer(String accessToken, long expiresIn, UserResponse user) {
        return new LoginResponse(accessToken, BEARER, expiresIn, user);
    }
}
