package com.athletiq.athlete;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class InvitationCodesTest {

    @Test
    void generatesEightCharactersWithoutAmbiguousOnes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String code = InvitationCodes.generate();
            assertThat(code).hasSize(8).matches("[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{8}").doesNotContainAnyWhitespaces();
            assertThat(code).doesNotContain("0", "O", "1", "I", "L");
            codes.add(code);
        }
        assertThat(codes).hasSizeGreaterThan(495);
    }

    @ParameterizedTest
    @ValueSource(strings = {"K7QH3MPA", "k7qh-3mpa", "  K7QH-3MPA ", "K7QH 3MPA", "k7Qh3mPa"})
    void normalizesWhatTheAthleteTypes(String input) {
        assertThat(InvitationCodes.normalize(input)).contains("K7QH3MPA");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"K7QH3MP", "K7QH3MPAA", "K7QH3MP0", "K7QH3MPI", "K7QH3MP!", "K7QH3MPÉ"})
    void rejectsMalformedCodes(String input) {
        assertThat(InvitationCodes.normalize(input)).isEmpty();
    }
}
