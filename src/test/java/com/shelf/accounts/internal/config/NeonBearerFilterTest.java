package com.shelf.accounts.internal.config;

import com.shelf.accounts.Member;
import com.shelf.accounts.internal.service.NeonMembers;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.KeyPairGenerator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NeonBearerFilterTest {
    private static final String ORIGIN = "https://neon.example";
    private static final String EMAIL = "student@example.invalid";

    @Test
    void authenticatesSignedSubjectAndIgnoresProviderRole() throws Exception {
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var members = mock(NeonMembers.class);
        var member = new Member(UUID.randomUUID(), EMAIL, "Student", true, "MEMBER");
        when(members.resolve("subject-1", EMAIL, "Student", true)).thenReturn(member);
        var filter = new NeonBearerFilter(verifier(keys.getPublic()), members);
        var claims = claims(ORIGIN, ORIGIN);
        claims.setClaim("role", "MODERATOR");
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + signed(claims, keys.getPrivate()));
        try {
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(member));
            verify(members).resolve("subject-1", EMAIL, "Student", true);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void rejectsWrongAudienceBeforeResolvingMember() throws Exception {
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var members = mock(NeonMembers.class);
        var filter = new NeonBearerFilter(verifier(keys.getPublic()), members);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer " + signed(claims(ORIGIN, "https://another.example"), keys.getPrivate()));
        var chain = mock(jakarta.servlet.FilterChain.class);
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain, members);
    }

    private JwtConsumer verifier(java.security.PublicKey key) {
        return new JwtConsumerBuilder().setRequireSubject().setRequireExpirationTime()
                .setExpectedIssuer(ORIGIN).setExpectedAudience(ORIGIN)
                .setJwsAlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT, "EdDSA")
                .setVerificationKey(key).build();
    }

    private JwtClaims claims(String issuer, String audience) {
        var claims = new JwtClaims();
        claims.setIssuer(issuer);
        claims.setAudience(audience);
        claims.setSubject("subject-1");
        claims.setExpirationTimeMinutesInTheFuture(5);
        claims.setClaim("email", EMAIL);
        claims.setClaim("name", "Student");
        claims.setClaim("emailVerified", true);
        return claims;
    }

    private String signed(JwtClaims claims, java.security.PrivateKey key) throws Exception {
        var signature = new JsonWebSignature();
        signature.setAlgorithmHeaderValue("EdDSA");
        signature.setKey(key);
        signature.setPayload(claims.toJson());
        return signature.getCompactSerialization();
    }
}
