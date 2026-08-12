package com.ratelimitservice.rls.domain.shared;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record ClientIp(String value) {

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");

    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^("
            + "([0-9a-f]{1,4}:){7}[0-9a-f]{1,4}|"
            + "([0-9a-f]{1,4}:){1,7}:|"
            + "([0-9a-f]{1,4}:){1,6}:[0-9a-f]{1,4}|"
            + "([0-9a-f]{1,4}:){1,5}(:[0-9a-f]{1,4}){1,2}|"
            + "([0-9a-f]{1,4}:){1,4}(:[0-9a-f]{1,4}){1,3}|"
            + "([0-9a-f]{1,4}:){1,3}(:[0-9a-f]{1,4}){1,4}|"
            + "([0-9a-f]{1,4}:){1,2}(:[0-9a-f]{1,4}){1,5}|"
            + "[0-9a-f]{1,4}:((:[0-9a-f]{1,4}){1,6})|"
            + ":((:[0-9a-f]{1,4}){1,7}|:)|"
            + "::(ffff(:0{1,4})?:)?((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)|"
            + "([0-9a-f]{1,4}:){1,4}:((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)"
            + ")$");

    public ClientIp {
        Objects.requireNonNull(value, "value must not be null");
        value = normalize(value);
    }

    private static String normalize(String rawValue) {
        String trimmed = rawValue.trim();
        if (IPV4_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }
        String lowerCased = trimmed.toLowerCase(Locale.ROOT);
        if (IPV6_PATTERN.matcher(lowerCased).matches()) {
            return lowerCased;
        }
        throw new IllegalArgumentException("Invalid IP address: " + rawValue);
    }
}
