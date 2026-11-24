package com.shelf.api.accounts.integration;

import com.shelf.api.common.error.SecurityErrors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
class SessionSecurityConfiguration {
    @Bean
    @Profile("!neon")
    SecurityFilterChain filterChain(HttpSecurity http, SecurityErrors errors, HttpSessionSecurityContextRepository repository,
            ObjectProvider<GoogleLoginHandler> google) throws Exception {
        var handler = google.getIfAvailable();
        if (handler != null) {
            http.oauth2Login(login -> login
                    .authorizationEndpoint(endpoint -> endpoint.baseUri("/api/oauth2/authorization"))
                    .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/login/oauth2/code/*"))
                    .successHandler(handler).failureHandler((request, response, error) -> {
                        response.setStatus(302);
                        response.setHeader("Location", "/account?error=google");
                    }));
        }
        return http
                .securityContext(context -> context.securityContextRepository(repository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/api/explore", "/api/institutions",
                                "/api/institutions/*/courses", "/api/auth/providers", "/api/capabilities", "/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                                "/api/auth/verify", "/api/auth/resend", "/api/auth/reset-request",
                                "/api/auth/reset").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .cors(Customizer.withDefaults())
                .addFilterAfter(new AuthenticatedUserFilter(), AnonymousAuthenticationFilter.class)
                .exceptionHandling(config -> config.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .deleteCookies("SHELF_SESSION")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .requestCache(cache -> cache.disable())
                .build();
    }
}
