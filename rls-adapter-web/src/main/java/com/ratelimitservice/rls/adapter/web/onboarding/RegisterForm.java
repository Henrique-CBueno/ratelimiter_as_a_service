package com.ratelimitservice.rls.adapter.web.onboarding;

/**
 * Backs the {@code POST /app/register} form. WebFlux's {@code @RequestParam} only binds query
 * parameters, never form-urlencoded body data (unlike the servlet stack's unified
 * {@code request.getParameter()}), so form submissions need a {@code @ModelAttribute} bean instead.
 */
public class RegisterForm {

    private String name;
    private String email;
    private String password;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
