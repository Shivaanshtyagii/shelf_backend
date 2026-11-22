package com.shelf.accounts.internal;

import com.shelf.accounts.Member;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
class AccountController {
    private final AccountService service;
    private final HttpSessionSecurityContextRepository securityContexts;

    AccountController(AccountService service, HttpSessionSecurityContextRepository securityContexts) {
        this.service = service;
        this.securityContexts = securityContexts;
    }

    @GetMapping("/csrf")
    ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("header", token.getHeaderName(), "token", token.getToken()));
    }

    @GetMapping("/me")
    Member me(Principal principal) { return service.current(principal); }

    @PutMapping("/me")
    Member update(Principal principal, @Valid @RequestBody Profile input) {
        return service.updateProfile(principal, input.displayName());
    }

    @PostMapping("/register")
    ResponseEntity<Void> register(@Valid @RequestBody Registration input) {
        service.register(input.email(), input.displayName(), input.password());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/login")
    Member login(@Valid @RequestBody Login input, HttpServletRequest request, HttpServletResponse response) {
        var authentication = service.login(input.email(), input.password());
        request.getSession();
        request.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContexts.saveContext(context, request, response);
        return service.current(authentication);
    }

    @PostMapping("/verify")
    ResponseEntity<Void> verify(@Valid @RequestBody Token input) {
        service.confirm(input.token(), "VERIFY", null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend")
    ResponseEntity<Void> resend(@Valid @RequestBody EmailAddress input) {
        service.requestChallenge(input.email(), "VERIFY");
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-request")
    ResponseEntity<Void> resetRequest(@Valid @RequestBody EmailAddress input) {
        service.requestChallenge(input.email(), "RESET");
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset")
    ResponseEntity<Void> reset(@Valid @RequestBody Reset input) {
        service.confirm(input.token(), "RESET", input.password());
        return ResponseEntity.noContent().build();
    }

    record Registration(@NotBlank @Email @Size(max = 254) String email,
                        @NotBlank @Size(max = 80) String displayName,
                        @NotBlank @Size(min = 12, max = 72) String password) {}
    record Login(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {}
    record Profile(@NotBlank @Size(max = 80) String displayName) {}
    record EmailAddress(@NotBlank @Email @Size(max = 254) String email) {}
    record Token(@NotBlank @Size(max = 100) String token) {}
    record Reset(@NotBlank @Size(max = 100) String token, @NotBlank @Size(min = 12, max = 72) String password) {}
}
