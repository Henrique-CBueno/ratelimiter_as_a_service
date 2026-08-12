package com.ratelimitservice.rls.adapter.web.auth;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the custom login page configured via {@code .formLogin(form -> form.loginPage("/app/login"))}
 * in {@code SecurityConfig}. Spring Security only redirects unauthenticated requests there — it
 * doesn't render the page itself once a custom {@code loginPage} is set.
 */
@Controller
public class LoginController {

    @GetMapping("/app/login")
    public String loginForm() {
        return "login";
    }
}
