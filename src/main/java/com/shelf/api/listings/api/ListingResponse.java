package com.shelf.api.listings.api;

import com.shelf.api.listings.api.Listing.Scope;
import com.shelf.api.listings.api.Listing.Status;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ListingResponse(UUID id, UUID ownerId, Scope scope, UUID institutionId, String title,
                              String description, String subject, int priceMinor, UUID assetId, Status status,
                              int revision, OffsetDateTime publishedAt) {
    public static ListingResponse from(Listing row) {
        return new ListingResponse(row.id(), row.ownerId(), row.scope(), row.institutionId(), row.title(),
                row.description(), row.subject(), row.priceMinor(), row.assetId(), row.status(), row.revision(),
                row.publishedAt());
    }
}
