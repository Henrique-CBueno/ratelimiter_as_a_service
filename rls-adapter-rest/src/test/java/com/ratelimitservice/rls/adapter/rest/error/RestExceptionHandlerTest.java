package com.ratelimitservice.rls.adapter.rest.error;

import com.ratelimitservice.rls.application.resource.port.DuplicateResourceKeyException;
import com.ratelimitservice.rls.application.tenant.port.DuplicateEmailException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RestExceptionHandlerTest {

    private final RestExceptionHandler handler = new RestExceptionHandler();

    @Test
    void duplicateEmailMapsToConflictWithTheExceptionMessageAsDetail() {
        ProblemDetail problem = handler.handleDuplicateEmail(new DuplicateEmailException("dup@acme.test"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).contains("dup@acme.test");
    }

    @Test
    void duplicateResourceKeyMapsToConflictWithTheExceptionMessageAsDetail() {
        ProblemDetail problem = handler.handleDuplicateResourceKey(new DuplicateResourceKeyException("/login"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).contains("/login");
    }

    @Test
    void resourceNotFoundMapsToNotFoundWithTheExceptionMessageAsDetail() {
        UUID id = UUID.randomUUID();
        ProblemDetail problem = handler.handleResourceNotFound(ResourceNotFoundException.forId(id));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).contains(id.toString());
    }
}
