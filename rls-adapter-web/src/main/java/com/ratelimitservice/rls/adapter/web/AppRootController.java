package com.ratelimitservice.rls.adapter.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * {@code /app/**} has no page of its own at the bare prefix — every real page lives one segment
 * deeper ({@code /app/login}, {@code /app/resources}, ...). Without this, an authenticated request
 * to {@code /app} or {@code /app/} fell through to the framework's default 404 instead of landing
 * somewhere useful (an unauthenticated request never hits this: {@code SecurityConfig} redirects it
 * to {@code /app/login} first).
 */
@Controller
public class AppRootController {

    @GetMapping({"/app", "/app/"})
    public String root() {
        return "redirect:/app/resources";
    }
}
