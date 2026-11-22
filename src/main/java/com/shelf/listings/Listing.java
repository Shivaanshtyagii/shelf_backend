package com.shelf.listings;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Listing(UUID id, UUID ownerId, Scope scope, UUID institutionId, String title, String description,
                      String subject, int priceMinor, UUID assetId, Status status, int revision, OffsetDateTime publishedAt) {
    public enum Scope { EXPLORE, CAMPUS_MATERIALS, CAMPUS_MARKETPLACE }
    public enum Status { DRAFT, PENDING, APPROVED, REJECTED, ARCHIVED }
}
