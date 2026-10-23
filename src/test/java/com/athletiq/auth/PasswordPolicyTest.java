package com.athletiq.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsAStrongPassword() {
        assertThat(PasswordPolicy.isValid("motdepasse123")).isTrue();
        assertThat(PasswordPolicy.isValid("abc-def_ghi456")).isTrue();
    }

    @Test
    void refusesTooShortPasswords() {
        assertThat(PasswordPolicy.isValid("court123")).isFalse();
        assertThat(PasswordPolicy.isValid("")).isFalse();
    }

    @Test
    void refusesTooLongPasswords() {
        assertThat(PasswordPolicy.isValid("a".repeat(73) + "1")).isFalse();
    }

    @Test
    void refusesPasswordsWithoutLetterOrDigit() {
        assertThat(PasswordPolicy.isValid("123456789012")).isFalse();
        assertThat(PasswordPolicy.isValid("motdepassepourtour")).isFalse();
    }

    @Test
    void refusesNullPassword() {
        assertThat(PasswordPolicy.isValid(null)).isFalse();
    }
}
