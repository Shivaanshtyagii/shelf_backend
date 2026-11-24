package com.shelf.accounts.internal.config;

import com.shelf.accounts.internal.service.NeonMembers;
import org.jose4j.http.Get;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwk.HttpsJwks;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.keys.resolvers.HttpsJwksVerificationKeyResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import java.net.URI;

@Configuration
@Profile("neon")
class NeonSecurityConfiguration {
    @Bean
    SecurityFilterChain neonFilterChain(HttpSecurity http, NeonMembers members,
            @Value("${shelf.neon.base-url}") String baseUrl,
            @Value("${shelf.neon.jwks}") String jwksUrl) throws Exception {
        var authUrl = URI.create(baseUrl);
        var jwks = URI.create(jwksUrl);
        if (!"https".equals(authUrl.getScheme()) || authUrl.getHost() == null
                || !"https".equals(jwks.getScheme()) || !authUrl.getAuthority().equals(jwks.getAuthority())
                || authUrl.getUserInfo() != null) {
            throw new IllegalArgumentException("Neon Auth issuer and JWKS must use HTTPS.");
        }
        var issuer = authUrl.getScheme() + "://" + authUrl.getAuthority();
        var keys = new HttpsJwks(jwksUrl);
        var network = new Get();
        network.setConnectTimeout(3000);
        network.setReadTimeout(3000);
        keys.setSimpleHttpGet(network);
        var tokens = new JwtConsumerBuilder()
                .setRequireExpirationTime().setRequireSubject()
                .setExpectedIssuer(issuer).setExpectedAudience(issuer)
                .setJwsAlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT, "EdDSA")
                .setVerificationKeyResolver(new HttpsJwksVerificationKeyResolver(keys))
                .build();
        return http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .logout(logout -> logout.disable())
                .addFilterBefore(new NeonBearerFilter(tokens, members), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/explore", "/api/institutions",
                                "/api/institutions/*/courses", "/api/capabilities", "/api/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/auth/me").authenticated()
                        .requestMatchers("/api/auth/**", "/api/oauth2/**", "/api/login/**").denyAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, ex) ->
                        response.sendError(401)))
                .build();
    }
}
