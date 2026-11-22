package com.shelf.listings.internal.model;

import com.shelf.listings.Listing.Scope;

import java.util.UUID;

public record ListingDraft(Scope scope, String title, String description, String subject, int priceMinor,
                           UUID assetId, int revision) {}
