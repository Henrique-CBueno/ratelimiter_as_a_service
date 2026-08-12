package com.ratelimitservice.rls.bootstrap;

import org.springframework.test.web.reactive.server.EntityExchangeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code WebTestClient} has no browser-like cookie jar or HTML form parsing, so hitting the
 * Thymeleaf pages end-to-end means manually carrying the session cookie between requests and
 * pulling the CSRF token out of the rendered form markup.
 */
final class WebFormTestSupport {

    private static final Pattern CSRF_INPUT = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    private WebFormTestSupport() {
    }

    static String csrfToken(EntityExchangeResult<String> result) {
        Matcher matcher = CSRF_INPUT.matcher(result.getResponseBody());
        if (!matcher.find()) {
            throw new IllegalStateException("No CSRF token found in response body: " + result.getResponseBody());
        }
        return matcher.group(1);
    }

    static String sessionCookie(EntityExchangeResult<?> result) {
        var cookie = result.getResponseCookies().getFirst("SESSION");
        if (cookie == null) {
            throw new IllegalStateException("No SESSION cookie set on response");
        }
        return cookie.getValue();
    }
}
