package com.shelf.api.pickup.web;

import com.shelf.api.pickup.domain.PickupRequest.Status;
import com.shelf.api.pickup.service.PickupService;
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
    private final PickupService pickups;

    PickupController(PickupService pickups) {
        this.pickups = pickups;
    }

    @GetMapping
    List<PickupResponse> mine(Principal principal) {
        return pickups.mine(principal).stream().map(PickupResponse::from).toList();
    }

    @PostMapping
    PickupResponse create(Principal principal, @Valid @RequestBody CreateRequest input) {
        return PickupResponse.from(pickups.create(principal, input.listingId()));
    }

    @PostMapping("/{id}")
    PickupResponse change(Principal principal, @PathVariable UUID id, @Valid @RequestBody ChangeRequest input) {
        return PickupResponse.from(pickups.change(principal, id, input.status(), input.meetingLocation(), input.meetingTime()));
    }

    record CreateRequest(@NotNull UUID listingId) {}
    record ChangeRequest(@NotNull Status status, @Size(max = 160) String meetingLocation, OffsetDateTime meetingTime) {}
}
