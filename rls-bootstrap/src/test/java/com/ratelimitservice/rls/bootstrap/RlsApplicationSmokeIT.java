package com.ratelimitservice.rls.bootstrap;

import org.junit.jupiter.api.Test;

class RlsApplicationSmokeIT extends AbstractE2ETest {

    @Test
    void contextLoadsWithEveryBeanWired() {
        // If the context fails to start (missing bean, Flyway migration failure, bad Redis/R2DBC
        // connection wiring), this test fails during Spring's context startup, before this body
        // ever runs.
    }

    @Test
    void openApiDocsAreReachable() {
        webTestClient.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths./api/v1/ratelimit/check").exists()
                .jsonPath("$.paths./api/v1/tenants").exists()
                .jsonPath("$.paths./api/v1/resources").exists();
    }

    @Test
    void swaggerUiIsReachable() {
        webTestClient.get().uri("/swagger-ui.html")
                .exchange()
                .expectStatus().is3xxRedirection();

        webTestClient.get().uri("/swagger-ui/index.html")
                .exchange()
                .expectStatus().isOk();
    }
}
