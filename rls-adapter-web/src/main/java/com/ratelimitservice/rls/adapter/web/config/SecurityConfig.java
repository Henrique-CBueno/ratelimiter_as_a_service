package com.ratelimitservice.rls.adapter.web.config;

import com.ratelimitservice.rls.adapter.web.auth.TenantReactiveAuthenticationManager;
import com.ratelimitservice.rls.application.tenant.LoginTenantUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

import java.net.URI;

/**
 * Two independent security chains, split by path prefix (design decision 3): {@code /app/**} gets
 * session-based form login, CSRF, and logout; {@code /api/v1/**} disables Spring Security's own
 * auth handling entirely, leaving the existing {@code ApiTokenAuthenticationWebFilter} (an
 * ordinary {@code WebFilter}, unrelated to this security config) as its sole auth mechanism,
 * unchanged from before this module existed.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public ReactiveAuthenticationManager tenantReactiveAuthenticationManager(LoginTenantUseCase loginTenantUseCase) {
        return new TenantReactiveAuthenticationManager(loginTenantUseCase);
    }

    @Bean
    @Order(1)
    public SecurityWebFilterChain webAppSecurityWebFilterChain(ServerHttpSecurity http,
                                                                 ReactiveAuthenticationManager authenticationManager) {
        RedirectServerAuthenticationSuccessHandler successHandler =
                new RedirectServerAuthenticationSuccessHandler("/app/resources");
        RedirectServerLogoutSuccessHandler logoutSuccessHandler = new RedirectServerLogoutSuccessHandler();
        logoutSuccessHandler.setLogoutSuccessUrl(URI.create("/app/login"));

        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/app/**"))
                .authenticationManager(authenticationManager)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/app/register", "/app/login").permitAll()
                        .anyExchange().authenticated())
                .formLogin(form -> form
                        .loginPage("/app/login")
                        .authenticationSuccessHandler(successHandler))
                .logout(logout -> logout
                        .logoutUrl("/app/logout")
                        .logoutSuccessHandler(logoutSuccessHandler))
                .build();
    }

    @Bean
    @Order(2)
    public SecurityWebFilterChain apiSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/api/v1/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
                .build();
    }
}
