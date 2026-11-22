package com.shelf.moderation.internal.model;

import com.shelf.listings.Listing;

import java.util.List;
import java.util.UUID;

public record ModerationQueue(List<Listing> listings, List<UUID> sellers, List<ModerationReport> reports,
                              List<ModerationAppeal> appeals) {}
