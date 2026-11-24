package com.shelf.api.moderation.web;

import com.shelf.api.listings.api.Listing;
import com.shelf.api.moderation.service.ModerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/moderation")
class ModerationController {
    private final ModerationService moderation;

    ModerationController(ModerationService moderation) {
        this.moderation = moderation;
    }

    @GetMapping("/queue")
    QueueResponse queue(Principal principal) {
        return QueueResponse.from(moderation.queue(principal));
    }

    @GetMapping("/listings/{id}/history")
    List<DecisionResponse> history(Principal principal, @PathVariable UUID id) {
        return moderation.history(id, principal).stream().map(DecisionResponse::from).toList();
    }

    @PostMapping("/listings/{id}")
    void decide(Principal principal, @PathVariable UUID id, @Valid @RequestBody ListingDecisionRequest input) {
        moderation.decideListing(id, principal, input.revision(), input.outcome(), input.reason());
    }

    @PostMapping("/listings/{id}/takedown")
    void takeDown(Principal principal, @PathVariable UUID id, @Valid @RequestBody ReasonRequest input) {
        moderation.takeDown(id, principal, input.reason());
    }

    @PostMapping("/sellers/{id}")
    void decideSeller(Principal principal, @PathVariable UUID id, @Valid @RequestBody DecisionRequest input) {
        moderation.decideSeller(id, principal, input.accepted(), input.reason());
    }

    @PostMapping("/listings/{id}/reports")
    void report(Principal principal, @PathVariable UUID id, @Valid @RequestBody ReasonRequest input) {
        moderation.report(id, principal, input.reason());
    }

    @PostMapping("/decisions/{id}/appeals")
    void appeal(Principal principal, @PathVariable long id, @Valid @RequestBody ReasonRequest input) {
        moderation.appeal(id, principal, input.reason());
    }

    @GetMapping("/mine")
    ActivityResponse activity(Principal principal) {
        return ActivityResponse.from(moderation.activity(principal));
    }

    @PostMapping("/reports/{id}")
    void reviewReport(Principal principal, @PathVariable UUID id, @Valid @RequestBody DecisionRequest input) {
        moderation.reviewReport(id, principal, input.accepted(), input.reason());
    }

    @PostMapping("/appeals/{id}")
    void reviewAppeal(Principal principal, @PathVariable UUID id, @Valid @RequestBody DecisionRequest input) {
        moderation.reviewAppeal(id, principal, input.accepted(), input.reason());
    }

    record ListingDecisionRequest(@Min(1) int revision, @NotNull Listing.Status outcome, @NotBlank @Size(max = 800) String reason) {}
    record DecisionRequest(boolean accepted, @NotBlank @Size(max = 800) String reason) {}
    record ReasonRequest(@NotBlank @Size(max = 800) String reason) {}
}
