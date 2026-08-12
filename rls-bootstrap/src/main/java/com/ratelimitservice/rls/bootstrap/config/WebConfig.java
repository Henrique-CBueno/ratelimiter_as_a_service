package com.ratelimitservice.rls.bootstrap.config;

import com.ratelimitservice.rls.adapter.rest.auth.ApiTokenAuthenticationWebFilter;
import com.ratelimitservice.rls.application.tenant.AuthenticateTenantUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link ApiTokenAuthenticationWebFilter} as a bean explicitly rather than making it a
 * {@code @Component}: it needs {@link AuthenticateTenantUseCase} constructor-injected, and
 * {@code rls-adapter-rest}'s own {@code @WebFluxTest} controller slices don't provide one — a
 * {@code @Component}-scanned filter would break those isolated slices by trying to autowire a
 * bean they don't define. Explicit wiring here avoids that entirely.
 */
@Configuration
public class WebConfig {

    @Bean
    public ApiTokenAuthenticationWebFilter apiTokenAuthenticationWebFilter(
            AuthenticateTenantUseCase authenticateTenantUseCase) {
        return new ApiTokenAuthenticationWebFilter(authenticateTenantUseCase);
    }
}
