package com.shelf.api.accounts.web;

import com.shelf.api.TestcontainersConfig;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ActiveProfiles("neon")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "shelf.neon.base-url=https://auth.example.invalid/db/auth",
        "shelf.neon.jwks=https://auth.example.invalid/db/auth/.well-known/jwks.json"})
class NeonProfileHttpTest extends TestcontainersConfig {
    @LocalServerPort int port;
    @MockitoBean JwtConsumer verifier;

    @Test
    void verifiedClaimsProvisionOneProfileAcrossRequests() throws Exception {
        // Signature and audience checks are covered separately with a real Ed25519 key.
        var subject = UUID.randomUUID().toString();
        var claims = new JwtClaims();
        claims.setSubject(subject);
        claims.setClaim("email", subject + "@example.invalid");
        claims.setClaim("emailVerified", true);
        when(verifier.processToClaims("test-token")).thenReturn(claims);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/me"))
                .header("Authorization", "Bearer test-token").GET().build();
        var client = HttpClient.newHttpClient();
        var first = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(first.statusCode()).isEqualTo(200);
        var profile = JsonMapper.builder().build().readTree(first.body());
        assertThat(profile.get("verified").asBoolean()).isTrue();
        assertThat(profile.get("role").asString()).isEqualTo("MEMBER");
        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).body()).isEqualTo(first.body());
    }
}
