package com.ratelimitservice.rls.domain.shared;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpTest {

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "127.0.0.1", "192.168.1.1", "255.255.255.255", "10.0.0.1"})
    void acceptsValidIpv4Addresses(String rawValue) {
        assertThat(new ClientIp(rawValue).value()).isEqualTo(rawValue);
    }

    @ParameterizedTest
    @ValueSource(strings = {"256.1.1.1", "1.2.3.4.5", "1.2.3", "abc.def.gha.bcd", ""})
    void rejectsInvalidIpv4Addresses(String rawValue) {
        assertThatThrownBy(() -> new ClientIp(rawValue))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
            "::1",
            "::",
            "fe80::1",
            "2001:db8::8a2e:370:7334",
            "::ffff:192.168.1.1"
    })
    void acceptsValidIpv6Addresses(String rawValue) {
        assertThat(new ClientIp(rawValue).value()).isEqualTo(rawValue.toLowerCase());
    }

    @ParameterizedTest
    @ValueSource(strings = {"gggg::1", "1:2:3:4:5:6:7:8:9", "not-an-ip"})
    void rejectsInvalidIpv6Addresses(String rawValue) {
        assertThatThrownBy(() -> new ClientIp(rawValue))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.api.Test
    void normalizesUppercaseIpv6ToLowercase() {
        ClientIp clientIp = new ClientIp("2001:DB8::1");
        assertThat(clientIp.value()).isEqualTo("2001:db8::1");
    }

    @org.junit.jupiter.api.Test
    void trimsSurroundingWhitespace() {
        ClientIp clientIp = new ClientIp("  192.168.1.1  ");
        assertThat(clientIp.value()).isEqualTo("192.168.1.1");
    }

    @org.junit.jupiter.api.Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> new ClientIp(null))
                .isInstanceOf(NullPointerException.class);
    }
}
