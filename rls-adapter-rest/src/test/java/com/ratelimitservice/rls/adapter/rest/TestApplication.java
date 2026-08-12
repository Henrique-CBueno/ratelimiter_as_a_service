package com.ratelimitservice.rls.adapter.rest;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * {@code rls-adapter-rest} is a library module composed by {@code rls-bootstrap} (group 11), not
 * a runnable application on its own. Spring Boot's test slices (e.g. {@code @WebFluxTest}) need a
 * {@code @SpringBootConfiguration} discoverable by searching packages upward from the test class —
 * this test-scope-only stand-in provides one. A bare {@code @SpringBootConfiguration} is not
 * enough: it doesn't imply {@code @ComponentScan}, so a test slice's controller-filtering scan has
 * nothing to scan and silently registers zero controllers. {@code @SpringBootApplication} (which
 * includes {@code @ComponentScan}) is the standard, documented stand-in for exactly this situation.
 */
@SpringBootApplication
class TestApplication {
}
