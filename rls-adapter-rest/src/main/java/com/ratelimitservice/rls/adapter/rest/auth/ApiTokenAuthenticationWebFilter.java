package com.ratelimitservice.rls.adapter.rest.auth;

import com.ratelimitservice.rls.application.tenant.AuthenticateTenantUseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Resolves the caller's {@code Authorization: Bearer <token>} header into a {@code Tenant} (via
 * {@link AuthenticateTenantUseCase}) and attaches it to the exchange under
 * {@link AuthenticatedTenant#ATTRIBUTE} for downstream handlers, or short-circuits with 401.
 * Tenant registration (and API documentation paths, once group 13 adds them) are allowlisted since
 * they must be reachable before a caller has a token.
 */
public class ApiTokenAuthenticationWebFilter implements WebFilter {

    private static final String PUBLIC_REGISTRATION_PATH = "/api/v1/tenants";
    private static final List<String> PUBLIC_PATH_PREFIXES = List.of("/v3/api-docs", "/swagger-ui", "/webjars");
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticateTenantUseCase authenticateTenantUseCase;

    public ApiTokenAuthenticationWebFilter(AuthenticateTenantUseCase authenticateTenantUseCase) {
        this.authenticateTenantUseCase = authenticateTenantUseCase;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (isAllowlisted(exchange)) {
            return chain.filter(exchange);
        }

        String rawToken = extractBearerToken(exchange);
        if (rawToken == null) {
            return unauthorized(exchange);
        }

        return authenticateTenantUseCase.authenticate(rawToken)
                .flatMap(tenant -> {
                    exchange.getAttributes().put(AuthenticatedTenant.ATTRIBUTE, tenant);
                    return chain.filter(exchange);
                })
                .switchIfEmpty(Mono.defer(() -> unauthorized(exchange)));
    }

    private boolean isAllowlisted(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        if (exchange.getRequest().getMethod() == HttpMethod.POST && PUBLIC_REGISTRATION_PATH.equals(path)) {
            return true;
        }
        return PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String extractBearerToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
