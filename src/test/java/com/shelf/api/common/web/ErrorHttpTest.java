package com.shelf.api.common.web;

import com.shelf.api.HttpTestSupport;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorHttpTest extends HttpTestSupport {
    @Test
    void securityAndMvcErrorsShareTheRequestIdAndProblemFormat() throws Exception {
        assertProblem("/auth/me", "support-case-42", 401);
        assertProblem("/explore?page=-1", "support-case-43", 400);
        assertProblem("/explore?sort=unknown", "support-case-44", 400);
    }

    private void assertProblem(String path, String id, int status) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path))
                .header("X-Request-Id", id).GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("X-Request-Id")).contains(id);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        var problem = JSON.readTree(response.body());
        assertThat(problem.get("status").asInt()).isEqualTo(status);
        assertThat(problem.get("title").asString()).isNotEmpty();
        assertThat(problem.get("detail").asString()).isNotEmpty();
        assertThat(problem.get("code").asString()).isNotEmpty();
        assertThat(problem.get("traceId").asString()).isEqualTo(id);
    }
}
