package com.shelf.api.listings.domain;

import com.shelf.api.listings.api.Listing.Scope;

import java.util.UUID;

public record ListingDraft(Scope scope, String title, String description, String subject, int priceMinor,
                           UUID assetId, int revision) {}
