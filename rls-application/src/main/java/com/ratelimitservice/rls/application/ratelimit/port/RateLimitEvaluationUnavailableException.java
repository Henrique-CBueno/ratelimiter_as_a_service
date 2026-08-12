package com.ratelimitservice.rls.application.ratelimit.port;

public final class RateLimitEvaluationUnavailableException extends RuntimeException {

    public RateLimitEvaluationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public RateLimitEvaluationUnavailableException(String message) {
        super(message);
    }
}
