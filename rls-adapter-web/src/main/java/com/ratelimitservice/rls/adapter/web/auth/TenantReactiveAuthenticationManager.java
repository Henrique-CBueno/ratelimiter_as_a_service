package com.ratelimitservice.rls.adapter.web.auth;

import com.ratelimitservice.rls.application.tenant.LoginTenantUseCase;
import com.ratelimitservice.rls.domain.tenant.Tenant;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

public final class TenantReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final LoginTenantUseCase loginTenantUseCase;

    public TenantReactiveAuthenticationManager(LoginTenantUseCase loginTenantUseCase) {
        this.loginTenantUseCase = loginTenantUseCase;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String email = authentication.getName();
        String password = String.valueOf(authentication.getCredentials());

        return loginTenantUseCase.login(email, password)
                .map(this::toAuthenticatedToken)
                .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid email or password")));
    }

    private Authentication toAuthenticatedToken(Tenant tenant) {
        TenantPrincipal principal = new TenantPrincipal(tenant.id(), tenant.email());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
}
