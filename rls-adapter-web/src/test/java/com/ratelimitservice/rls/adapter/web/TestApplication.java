package com.ratelimitservice.rls.adapter.web;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * {@code rls-adapter-web} is a library module composed by {@code rls-bootstrap}, not a runnable
 * application on its own. Spring Boot's test slices (e.g. {@code @WebFluxTest}) need a
 * {@code @SpringBootConfiguration} discoverable by searching packages upward from the test class —
 * see {@code rls-adapter-rest}'s identical stand-in for why this must be
 * {@code @SpringBootApplication} (which implies {@code @ComponentScan}) rather than a bare
 * {@code @SpringBootConfiguration}.
 */
@SpringBootApplication
class TestApplication {
}
