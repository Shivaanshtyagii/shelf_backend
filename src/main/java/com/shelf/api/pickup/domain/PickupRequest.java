package com.shelf.api.pickup.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PickupRequest(UUID id, UUID listingId, String listingTitle, UUID institutionId, UUID buyerId,
                            UUID sellerId, Status status, String meetingLocation, OffsetDateTime meetingTime,
                            OffsetDateTime createdAt) {
    public enum Status { REQUESTED, ACCEPTED, DECLINED, CANCELLED, COLLECTED }
}
