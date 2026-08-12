package com.ratelimitservice.rls.adapter.rest.ratelimit;

public record CheckRequest(String resource, String clientIp) {
}
