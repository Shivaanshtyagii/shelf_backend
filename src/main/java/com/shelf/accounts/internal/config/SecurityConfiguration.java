package com.shelf.accounts.internal.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    DefaultCookieSerializer cookieSerializer(@Value("${shelf.secure-cookie:true}") boolean secure) {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SHELF_SESSION");
        serializer.setUseSecureCookie(secure);
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setCookiePath("/");
        return serializer;
    }

    @Bean
    @org.springframework.context.annotation.Profile("!neon")
    SecurityFilterChain filterChain(HttpSecurity http, HttpSessionSecurityContextRepository repository,
            org.springframework.beans.factory.ObjectProvider<GoogleLoginHandler> google) throws Exception {
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
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, ex) ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .deleteCookies("SHELF_SESSION")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .requestCache(cache -> cache.disable())
                .build();
    }
}
