package com.ratelimitservice.rls.adapter.web.onboarding;

import com.ratelimitservice.rls.application.tenant.RegisterTenantUseCase;
import com.ratelimitservice.rls.application.tenant.port.DuplicateEmailException;
import com.ratelimitservice.rls.domain.shared.FallbackPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.reactive.result.view.Rendering;
import reactor.core.publisher.Mono;

@org.springframework.stereotype.Controller
public class RegistrationController {

    private final RegisterTenantUseCase registerTenantUseCase;

    public RegistrationController(RegisterTenantUseCase registerTenantUseCase) {
        this.registerTenantUseCase = registerTenantUseCase;
    }

    @GetMapping("/app/register")
    public Rendering registerForm() {
        return Rendering.view("register").build();
    }

    @PostMapping("/app/register")
    public Mono<Rendering> register(@ModelAttribute RegisterForm form) {
        return registerTenantUseCase.register(form.getName(), form.getEmail(), form.getPassword(), FallbackPolicy.FAIL_OPEN)
                .map(result -> Rendering.view("register-success")
                        .modelAttribute("tenantId", result.tenantId().value())
                        .modelAttribute("apiToken", result.apiToken())
                        .build())
                .onErrorResume(DuplicateEmailException.class, ex -> Mono.just(Rendering.view("register")
                        .modelAttribute("error", ex.getMessage())
                        .modelAttribute("name", form.getName())
                        .modelAttribute("email", form.getEmail())
                        .status(HttpStatus.CONFLICT)
                        .build()));
    }
}
