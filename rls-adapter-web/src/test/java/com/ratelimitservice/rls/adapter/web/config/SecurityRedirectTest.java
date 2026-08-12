package com.ratelimitservice.rls.adapter.web.config;

import com.ratelimitservice.rls.application.resource.CreateResourceUseCase;
import com.ratelimitservice.rls.application.resource.DeleteResourceUseCase;
import com.ratelimitservice.rls.application.resource.GetResourceUseCase;
import com.ratelimitservice.rls.application.resource.ListResourcesUseCase;
import com.ratelimitservice.rls.application.resource.UpdateResourceUseCase;
import com.ratelimitservice.rls.application.tenant.LoginTenantUseCase;
import com.ratelimitservice.rls.adapter.web.resource.ResourceDashboardController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(ResourceDashboardController.class)
@Import(SecurityConfig.class)
class SecurityRedirectTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private LoginTenantUseCase loginTenantUseCase;
    @MockitoBean
    private ListResourcesUseCase listResourcesUseCase;
    @MockitoBean
    private CreateResourceUseCase createResourceUseCase;
    @MockitoBean
    private GetResourceUseCase getResourceUseCase;
    @MockitoBean
    private UpdateResourceUseCase updateResourceUseCase;
    @MockitoBean
    private DeleteResourceUseCase deleteResourceUseCase;

    @Test
    void unauthenticatedDashboardRequestRedirectsToLogin() {
        webTestClient.get().uri("/app/resources")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*/app/login.*");
    }
}
