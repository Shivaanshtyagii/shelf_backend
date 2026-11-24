package com.shelf.api.pickup.web;

import com.shelf.api.pickup.domain.PickupRequest.Status;
import com.shelf.api.pickup.domain.PickupRequest;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PickupResponse(UUID id, UUID listingId, String listingTitle, UUID institutionId, UUID buyerId,
                             UUID sellerId, Status status, String meetingLocation, OffsetDateTime meetingTime,
                             OffsetDateTime createdAt) {
    public static PickupResponse from(PickupRequest row) {
        return new PickupResponse(row.id(), row.listingId(), row.listingTitle(), row.institutionId(), row.buyerId(),
                row.sellerId(), row.status(), row.meetingLocation(), row.meetingTime(), row.createdAt());
    }
}
