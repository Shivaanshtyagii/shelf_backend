package com.shelf.pickup.internal.controller;

import com.shelf.accounts.Accounts;
import com.shelf.pickup.internal.model.PickupRequest;
import com.shelf.pickup.internal.model.PickupRequest.Status;
import com.shelf.pickup.internal.service.PickupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pickup")
class PickupController {
    private final Accounts accounts;
    private final PickupService pickups;

    PickupController(Accounts accounts, PickupService pickups) {
        this.accounts = accounts;
        this.pickups = pickups;
    }

    @GetMapping
    List<PickupRequest> mine(Principal principal) { return pickups.mine(accounts.current(principal).id()); }

    @PostMapping
    PickupRequest create(Principal principal, @Valid @RequestBody Create input) {
        return pickups.create(accounts.verified(principal), input.listingId());
    }

    @PostMapping("/{id}")
    PickupRequest change(Principal principal, @PathVariable UUID id, @Valid @RequestBody Change input) {
        return pickups.change(accounts.verified(principal), id, input.status(), input.meetingLocation(), input.meetingTime());
    }

    record Create(@NotNull UUID listingId) {}
    record Change(@NotNull Status status, @Size(max = 160) String meetingLocation, OffsetDateTime meetingTime) {}
}
