package com.shelf.api.listings.api;

import com.shelf.api.listings.api.Listing.Scope;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ListingPageResponse(List<Card> items, int page, boolean hasNext) {
    public record Card(UUID id, Scope scope, String title, String description, String subject,
                       int priceMinor, OffsetDateTime publishedAt) {
        public static Card from(Listing row) {
            return new Card(row.id(), row.scope(), row.title(), row.description(), row.subject(), row.priceMinor(),
                row.publishedAt());
        }
    }
}
