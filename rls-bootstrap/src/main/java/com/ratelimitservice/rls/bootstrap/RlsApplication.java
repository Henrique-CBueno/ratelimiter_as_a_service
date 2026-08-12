package com.ratelimitservice.rls.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition root (design decision 10): the only module allowed to depend on every other
 * module, wiring {@code rls-application}'s use cases to the concrete adapters from
 * {@code rls-adapter-redis}, {@code rls-adapter-persistence}, {@code rls-adapter-resilience}, and
 * {@code rls-adapter-rest}. Component scanning is widened to the whole {@code com.ratelimitservice.rls}
 * tree since the controllers/filters/advice live outside this module's own package.
 */
@SpringBootApplication(scanBasePackages = "com.ratelimitservice.rls")
public class RlsApplication {

    public static void main(String[] args) {
        SpringApplication.run(RlsApplication.class, args);
    }
}
