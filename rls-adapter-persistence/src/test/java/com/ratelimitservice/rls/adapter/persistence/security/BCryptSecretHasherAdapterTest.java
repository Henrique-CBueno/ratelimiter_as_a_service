package com.ratelimitservice.rls.adapter.persistence.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BCryptSecretHasherAdapterTest {

    private final BCryptSecretHasherAdapter hasher = new BCryptSecretHasherAdapter();

    @Test
    void hashOfASecretMatchesThatSameSecret() {
        String hash = hasher.hash("correct-horse-battery-staple");

        assertThat(hasher.matches("correct-horse-battery-staple", hash)).isTrue();
    }

    @Test
    void hashDoesNotMatchADifferentSecret() {
        String hash = hasher.hash("correct-horse-battery-staple");

        assertThat(hasher.matches("wrong-secret", hash)).isFalse();
    }

    @Test
    void hashIsNotEqualToTheRawSecret() {
        String rawSecret = "correct-horse-battery-staple";

        String hash = hasher.hash(rawSecret);

        assertThat(hash).isNotEqualTo(rawSecret);
    }

    @Test
    void hashingTheSameSecretTwiceProducesDifferentHashes() {
        String rawSecret = "correct-horse-battery-staple";

        String firstHash = hasher.hash(rawSecret);
        String secondHash = hasher.hash(rawSecret);

        assertThat(firstHash).isNotEqualTo(secondHash);
        assertThat(hasher.matches(rawSecret, firstHash)).isTrue();
        assertThat(hasher.matches(rawSecret, secondHash)).isTrue();
    }
}
