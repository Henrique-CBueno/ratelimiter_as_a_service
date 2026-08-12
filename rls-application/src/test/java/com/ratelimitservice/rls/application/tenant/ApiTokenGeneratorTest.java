package com.ratelimitservice.rls.application.tenant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiTokenGeneratorTest {

    @Test
    void generatesATokenWithTheExpectedPrefix() {
        String token = ApiTokenGenerator.generate();

        assertThat(token).startsWith("rls_live_");
    }

    @Test
    void generatesDifferentTokensOnEachCall() {
        String first = ApiTokenGenerator.generate();
        String second = ApiTokenGenerator.generate();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void displayPrefixIsTheFirstTwelveCharacters() {
        String token = "rls_live_abcdefghijklmnop";

        assertThat(ApiTokenGenerator.displayPrefix(token)).isEqualTo("rls_live_abc");
    }

    @Test
    void fingerprintIsDeterministicForTheSameToken() {
        String token = ApiTokenGenerator.generate();

        String first = ApiTokenGenerator.fingerprint(token);
        String second = ApiTokenGenerator.fingerprint(token);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void fingerprintDiffersForDifferentTokens() {
        String tokenA = ApiTokenGenerator.generate();
        String tokenB = ApiTokenGenerator.generate();

        assertThat(ApiTokenGenerator.fingerprint(tokenA)).isNotEqualTo(ApiTokenGenerator.fingerprint(tokenB));
    }

    @Test
    void fingerprintDoesNotContainTheRawToken() {
        String token = ApiTokenGenerator.generate();

        assertThat(ApiTokenGenerator.fingerprint(token)).doesNotContain(token);
    }
}
