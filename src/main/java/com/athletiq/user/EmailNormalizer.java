package com.athletiq.user;

import java.util.Locale;

/** Normalisation unique des e-mails : espaces retirés et minuscules. */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
