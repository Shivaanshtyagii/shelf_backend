package com.shelf.moderation.internal.controller;

import com.shelf.accounts.Accounts;
import com.shelf.listings.Listing;
import com.shelf.moderation.internal.model.ModerationActivity;
import com.shelf.moderation.internal.model.ModerationDecision;
import com.shelf.moderation.internal.model.ModerationQueue;
import com.shelf.moderation.internal.service.ModerationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/moderation")
class ModerationController {
    private final Accounts accounts;
    private final ModerationService moderation;

    ModerationController(Accounts accounts, ModerationService moderation) {
        this.accounts = accounts;
        this.moderation = moderation;
    }

    @GetMapping("/queue")
    ModerationQueue queue(Principal principal) {
        accounts.moderator(principal);
        return moderation.queue();
    }

    @GetMapping("/listings/{id}/history")
    List<ModerationDecision> history(Principal principal, @PathVariable UUID id) {
        return moderation.history(id, accounts.verified(principal));
    }

    @PostMapping("/listings/{id}")
    void decide(Principal principal, @PathVariable UUID id, @Valid @RequestBody ListingDecision input) {
        moderation.decideListing(id, accounts.moderator(principal).id(), input.revision(), input.outcome(), input.reason());
    }

    @PostMapping("/listings/{id}/takedown")
    void takeDown(Principal principal, @PathVariable UUID id, @Valid @RequestBody Reason input) {
        moderation.takeDown(id, accounts.moderator(principal).id(), input.reason());
    }

    @PostMapping("/sellers/{id}")
    void decideSeller(Principal principal, @PathVariable UUID id, @Valid @RequestBody Decision input) {
        moderation.decideSeller(id, accounts.moderator(principal).id(), input.accepted(), input.reason());
    }

    @PostMapping("/listings/{id}/reports")
    void report(Principal principal, @PathVariable UUID id, @Valid @RequestBody Reason input) {
        moderation.report(id, accounts.verified(principal), input.reason());
    }

    @PostMapping("/decisions/{id}/appeals")
    void appeal(Principal principal, @PathVariable long id, @Valid @RequestBody Reason input) {
        moderation.appeal(id, accounts.verified(principal).id(), input.reason());
    }

    @GetMapping("/mine")
    ModerationActivity activity(Principal principal) {
        return moderation.activity(accounts.current(principal).id());
    }

    @PostMapping("/reports/{id}")
    void reviewReport(Principal principal, @PathVariable UUID id, @Valid @RequestBody Decision input) {
        moderation.reviewReport(id, accounts.moderator(principal).id(), input.accepted(), input.reason());
    }

    @PostMapping("/appeals/{id}")
    void reviewAppeal(Principal principal, @PathVariable UUID id, @Valid @RequestBody Decision input) {
        moderation.reviewAppeal(id, accounts.moderator(principal).id(), input.accepted(), input.reason());
    }

    record ListingDecision(@Min(1) int revision, @NotNull Listing.Status outcome, @NotBlank @Size(max = 800) String reason) {}
    record Decision(boolean accepted, @NotBlank @Size(max = 800) String reason) {}
    record Reason(@NotBlank @Size(max = 800) String reason) {}
}
