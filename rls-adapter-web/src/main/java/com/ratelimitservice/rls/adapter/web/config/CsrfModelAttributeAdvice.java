package com.ratelimitservice.rls.adapter.web.config;

import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Spring WebFlux's Thymeleaf integration, unlike the servlet stack, does not automatically expose
 * the CSRF token as a template variable — {@code CsrfWebFilter} only stores it as an exchange
 * attribute. This makes it available to every {@code /app/**} template as {@code ${_csrf}}.
 */
@ControllerAdvice
public class CsrfModelAttributeAdvice {

    @ModelAttribute("_csrf")
    public Mono<CsrfToken> csrfToken(ServerWebExchange exchange) {
        Mono<CsrfToken> token = exchange.getAttribute(CsrfToken.class.getName());
        return token != null ? token : Mono.empty();
    }
}
