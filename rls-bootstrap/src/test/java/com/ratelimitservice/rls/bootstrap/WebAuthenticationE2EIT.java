package com.ratelimitservice.rls.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;

import static com.ratelimitservice.rls.bootstrap.WebFormTestSupport.csrfToken;
import static com.ratelimitservice.rls.bootstrap.WebFormTestSupport.sessionCookie;

class WebAuthenticationE2EIT extends AbstractE2ETest {

    @Test
    void unauthenticatedDashboardRequestRedirectsToLogin() {
        webTestClient.get().uri("/app/resources")
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*/app/login");
    }

    @Test
    void logoutInvalidatesTheSessionSoTheDashboardRedirectsAgain() {
        EntityExchangeResult<String> registerForm = webTestClient.get().uri("/app/register")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();
        String session = sessionCookie(registerForm);
        String registerCsrf = csrfToken(registerForm);

        String email = "logout-e2e@acme.test";
        webTestClient.post().uri("/app/register")
                .cookie("SESSION", session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData("_csrf", registerCsrf, "name", "Acme Inc",
                        "email", email, "password", "s3cret")))
                .exchange()
                .expectStatus().isOk();

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
                .expectBody()
                .returnResult();
        String authenticatedSession = sessionCookie(loginResult);

        webTestClient.get().uri("/app/resources")
                .cookie("SESSION", authenticatedSession)
                .exchange()
                .expectStatus().isOk();

        EntityExchangeResult<String> dashboard = webTestClient.get().uri("/app/resources")
                .cookie("SESSION", authenticatedSession)
                .exchange()
                .expectBody(String.class)
                .returnResult();
        String logoutCsrf = csrfToken(dashboard);

        webTestClient.post().uri("/app/logout")
                .cookie("SESSION", authenticatedSession)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData("_csrf", logoutCsrf)))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*/app/login");

        webTestClient.get().uri("/app/resources")
                .cookie("SESSION", authenticatedSession)
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*/app/login");
    }

    private static MultiValueMap<String, String> formData(String... keyValuePairs) {
        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            data.add(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return data;
    }
}
