package com.shelf.pickup.internal;

import com.shelf.accounts.Accounts;
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
    private final Pickups pickups;

    PickupController(Accounts accounts, Pickups pickups) {
        this.accounts = accounts;
        this.pickups = pickups;
    }

    @GetMapping
    List<Pickups.Request> mine(Principal principal) { return pickups.mine(accounts.current(principal).id()); }

    @PostMapping
    Pickups.Request create(Principal principal, @Valid @RequestBody Create input) {
        return pickups.create(accounts.verified(principal), input.listingId());
    }

    @PostMapping("/{id}")
    Pickups.Request change(Principal principal, @PathVariable UUID id, @Valid @RequestBody Change input) {
        return pickups.change(accounts.verified(principal), id, input.status(), input.meetingLocation(), input.meetingTime());
    }

    record Create(@NotNull UUID listingId) {}
    record Change(@NotNull Pickups.Status status, @Size(max = 160) String meetingLocation, OffsetDateTime meetingTime) {}
}
