package com.ratelimitservice.rls.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ratelimitservice.rls.bootstrap.WebFormTestSupport.csrfToken;
import static com.ratelimitservice.rls.bootstrap.WebFormTestSupport.sessionCookie;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the full web dashboard flow through real HTTP requests: register, log in, create a
 * resource, edit it, delete it, and rotate the API token — exercising
 * {@code web-tenant-onboarding}, {@code web-authentication}, and {@code web-resource-dashboard}
 * together against real Redis (rate-limit state + Spring Session) and PostgreSQL.
 */
class WebOnboardingAndDashboardE2EIT extends AbstractE2ETest {

    private static final Pattern EDIT_LINK = Pattern.compile("/app/resources/([0-9a-fA-F-]{36})/edit");

    @Test
    void registersLogsInAndManagesAResourceThroughTheDashboard() {
        EntityExchangeResult<String> registerForm = webTestClient.get().uri("/app/register")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        String session = sessionCookie(registerForm);
        String registerCsrf = csrfToken(registerForm);

        String email = "dashboard-e2e@acme.test";
        webTestClient.post().uri("/app/register")
                .cookie("SESSION", session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData("_csrf", registerCsrf, "name", "Acme Inc",
                        "email", email, "password", "s3cret")))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(result -> assertThat(new String(result.getResponseBody()))
                        .contains("api-token"));

        EntityExchangeResult<String> loginForm = webTestClient.get().uri("/app/login")
                .cookie("SESSION", session)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        String loginCsrf = csrfToken(loginForm);

        EntityExchangeResult<byte[]> loginResult = webTestClient.post().uri("/app/login")
                .cookie("SESSION", session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData("_csrf", loginCsrf, "username", email, "password", "s3cret")))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*/app/resources")
                .expectBody()
                .returnResult();
        String authenticatedSession = sessionCookie(loginResult);

        EntityExchangeResult<String> newResourceForm = webTestClient.get().uri("/app/resources/new")
                .cookie("SESSION", authenticatedSession)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        String createCsrf = csrfToken(newResourceForm);

        webTestClient.post().uri("/app/resources")
                .cookie("SESSION", authenticatedSession)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData("_csrf", createCsrf, "resourceKey", "/dashboard-resource",
                        "strategyType", "FIXED_WINDOW", "limit", "10", "windowSeconds", "60")))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*/app/resources");

        EntityExchangeResult<String> listAfterCreate = webTestClient.get().uri("/app/resources")
                .cookie("SESSION", authenticatedSession)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        assertThat(listAfterCreate.getResponseBody()).contains("/dashboard-resource");

        Matcher editLinkMatcher = EDIT_LINK.matcher(listAfterCreate.getResponseBody());
        assertThat(editLinkMatcher.find()).as("edit link for the created resource").isTrue();
        String resourceId = editLinkMatcher.group(1);

        EntityExchangeResult<String> editForm = webTestClient.get().uri("/app/resources/{id}/edit", resourceId)
                .cookie("SESSION", authenticatedSession)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        String editCsrf = csrfToken(editForm);

        webTestClient.post().uri("/app/resources/{id}", resourceId)
                .cookie("SESSION", authenticatedSession)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData("_csrf", editCsrf, "strategyType", "TOKEN_BUCKET",
                        "limit", "25", "windowSeconds", "30")))
                .exchange()
                .expectStatus().is3xxRedirection();

        EntityExchangeResult<String> listAfterEdit = webTestClient.get().uri("/app/resources")
                .cookie("SESSION", authenticatedSession)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        assertThat(listAfterEdit.getResponseBody()).contains("TOKEN_BUCKET").contains("25");
        String deleteCsrf = csrfToken(listAfterEdit);

        webTestClient.post().uri("/app/resources/{id}/delete", resourceId)
                .cookie("SESSION", authenticatedSession)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData("_csrf", deleteCsrf)))
                .exchange()
                .expectStatus().is3xxRedirection();

        EntityExchangeResult<String> listAfterDelete = webTestClient.get().uri("/app/resources")
                .cookie("SESSION", authenticatedSession)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        assertThat(listAfterDelete.getResponseBody()).containsPattern("<td[^>]*>false</td>");

        EntityExchangeResult<String> settingsPage = webTestClient.get().uri("/app/settings")
                .cookie("SESSION", authenticatedSession)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        String rotateCsrf = csrfToken(settingsPage);

        webTestClient.post().uri("/app/settings/rotate")
                .cookie("SESSION", authenticatedSession)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData("_csrf", rotateCsrf)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .consumeWith(result -> assertThat(new String(result.getResponseBody())).contains("api-token"));
    }

    private static MultiValueMap<String, String> formData(String... keyValuePairs) {
        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            data.add(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return data;
    }
}
