package com.ratelimitservice.rls.adapter.rest.resource;

import com.ratelimitservice.rls.adapter.rest.auth.FakeAuthenticatedTenantFilterConfig;
import com.ratelimitservice.rls.adapter.rest.error.RestExceptionHandler;
import com.ratelimitservice.rls.application.resource.CreateResourceUseCase;
import com.ratelimitservice.rls.application.resource.DeleteResourceUseCase;
import com.ratelimitservice.rls.application.resource.GetResourceUseCase;
import com.ratelimitservice.rls.application.resource.ListResourcesUseCase;
import com.ratelimitservice.rls.application.resource.UpdateResourceUseCase;
import com.ratelimitservice.rls.application.resource.port.DuplicateResourceKeyException;
import com.ratelimitservice.rls.domain.ratelimit.Quota;
import com.ratelimitservice.rls.domain.ratelimit.StrategyType;
import com.ratelimitservice.rls.domain.resource.RateLimitResource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

import static com.ratelimitservice.rls.adapter.rest.auth.FakeAuthenticatedTenantFilterConfig.TENANT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(ResourceController.class)
@Import({RestExceptionHandler.class, FakeAuthenticatedTenantFilterConfig.class})
class ResourceControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CreateResourceUseCase createResourceUseCase;
    @MockitoBean
    private ListResourcesUseCase listResourcesUseCase;
    @MockitoBean
    private GetResourceUseCase getResourceUseCase;
    @MockitoBean
    private UpdateResourceUseCase updateResourceUseCase;
    @MockitoBean
    private DeleteResourceUseCase deleteResourceUseCase;

    private static RateLimitResource sampleResource() {
        return RateLimitResource.create(TENANT.id(), "/login", StrategyType.FIXED_WINDOW, Quota.of(10, Duration.ofMinutes(1)));
    }

    @Test
    void createReturnsCreatedWithTheNewResource() {
        RateLimitResource resource = sampleResource();
        when(createResourceUseCase.create(eq(TENANT.id()), any(), any(), any(), any())).thenReturn(Mono.just(resource));

        webTestClient.post().uri("/api/v1/resources")
                .bodyValue(new CreateResourceRequest("/login", StrategyType.FIXED_WINDOW, 10, 60, null, null))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.resourceKey").isEqualTo("/login");
    }

    @Test
    void createWithDuplicateKeyReturnsConflict() {
        when(createResourceUseCase.create(eq(TENANT.id()), any(), any(), any(), any()))
                .thenReturn(Mono.error(new DuplicateResourceKeyException("/login")));

        webTestClient.post().uri("/api/v1/resources")
                .bodyValue(new CreateResourceRequest("/login", StrategyType.FIXED_WINDOW, 10, 60, null, null))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void listReturnsTheTenantsResources() {
        when(listResourcesUseCase.list(TENANT.id())).thenReturn(Flux.just(sampleResource()));

        webTestClient.get().uri("/api/v1/resources")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ResourceResponse.class)
                .hasSize(1);
    }

    @Test
    void getOwnResourceReturnsOk() {
        RateLimitResource resource = sampleResource();
        when(getResourceUseCase.get(eq(TENANT.id()), eq(resource.id()))).thenReturn(Mono.just(resource));

        webTestClient.get().uri("/api/v1/resources/{id}", resource.id().value())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getUnknownOrOtherTenantsResourceReturnsNotFound() {
        UUID id = UUID.randomUUID();
        when(getResourceUseCase.get(any(), any())).thenReturn(Mono.empty());

        webTestClient.get().uri("/api/v1/resources/{id}", id)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void updateOwnResourceReturnsOk() {
        RateLimitResource resource = sampleResource();
        when(updateResourceUseCase.update(eq(TENANT.id()), eq(resource.id()), any(), any())).thenReturn(Mono.just(resource));

        webTestClient.put().uri("/api/v1/resources/{id}", resource.id().value())
                .bodyValue(new UpdateResourceRequest(StrategyType.TOKEN_BUCKET, 5, 30, 5))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void updateUnknownOrOtherTenantsResourceReturnsNotFound() {
        UUID id = UUID.randomUUID();
        when(updateResourceUseCase.update(any(), any(), any(), any())).thenReturn(Mono.empty());

        webTestClient.put().uri("/api/v1/resources/{id}", id)
                .bodyValue(new UpdateResourceRequest(StrategyType.TOKEN_BUCKET, 5, 30, 5))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void deleteOwnResourceReturnsNoContent() {
        UUID id = UUID.randomUUID();
        when(deleteResourceUseCase.delete(eq(TENANT.id()), any())).thenReturn(Mono.just(true));

        webTestClient.delete().uri("/api/v1/resources/{id}", id)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void deleteUnknownOrOtherTenantsResourceReturnsNotFound() {
        UUID id = UUID.randomUUID();
        when(deleteResourceUseCase.delete(any(), any())).thenReturn(Mono.just(false));

        webTestClient.delete().uri("/api/v1/resources/{id}", id)
                .exchange()
                .expectStatus().isNotFound();
    }
}
