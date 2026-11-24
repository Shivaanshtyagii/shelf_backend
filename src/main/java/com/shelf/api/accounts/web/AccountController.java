package com.shelf.api.accounts.web;

import com.shelf.api.accounts.domain.ChallengePurpose;
import com.shelf.api.accounts.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth")
class AccountController {
    private final AccountService service;
    private final SessionLogin sessions;
    private final boolean googleEnabled;

    AccountController(AccountService service, SessionLogin sessions,
                      Environment environment) {
        this.googleEnabled = environment.matchesProfiles("google");
        this.service = service;
        this.sessions = sessions;
    }

    @GetMapping("/csrf")
    ResponseEntity<CsrfResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(token.getHeaderName(), token.getToken()));
    }

    @GetMapping("/providers")
    ProvidersResponse providers() {
        return new ProvidersResponse(googleEnabled);
    }

    @GetMapping("/me")
    ProfileResponse me(Principal principal) { return ProfileResponse.from(service.current(principal)); }

    @PutMapping("/me")
    ProfileResponse update(Principal principal, @Valid @RequestBody ProfileRequest input) {
        return ProfileResponse.from(service.updateProfile(principal, input.displayName()));
    }

    @PostMapping("/register")
    ResponseEntity<Void> register(@Valid @RequestBody RegistrationRequest input) {
        service.register(input.email(), input.displayName(), input.password());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/login")
    ProfileResponse login(@Valid @RequestBody LoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        var authentication = service.login(input.email(), input.password());
        sessions.establish(authentication, request, response);
        return ProfileResponse.from(service.current(authentication));
    }

    @PostMapping("/verify")
    ResponseEntity<Void> verify(@Valid @RequestBody TokenRequest input) {
        service.confirm(input.token(), ChallengePurpose.VERIFY, null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend")
    ResponseEntity<Void> resend(@Valid @RequestBody EmailAddressRequest input) {
        service.requestChallenge(input.email(), ChallengePurpose.VERIFY);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-request")
    ResponseEntity<Void> resetRequest(@Valid @RequestBody EmailAddressRequest input) {
        service.requestChallenge(input.email(), ChallengePurpose.RESET);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset")
    ResponseEntity<Void> reset(@Valid @RequestBody ResetRequest input) {
        service.confirm(input.token(), ChallengePurpose.RESET, input.password());
        return ResponseEntity.noContent().build();
    }

    record CsrfResponse(String header, String token) {}
    record ProvidersResponse(boolean google) {}

    record RegistrationRequest(@NotBlank @Email @Size(max = 254) String email,
                        @NotBlank @Size(max = 80) String displayName,
                        @NotBlank @Size(min = 12, max = 72) String password) {}
    record LoginRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {}
    record ProfileRequest(@NotBlank @Size(max = 80) String displayName) {}
    record EmailAddressRequest(@NotBlank @Email @Size(max = 254) String email) {}
    record TokenRequest(@NotBlank @Size(max = 100) String token) {}
    record ResetRequest(@NotBlank @Size(max = 100) String token, @NotBlank @Size(min = 12, max = 72) String password) {}
}
