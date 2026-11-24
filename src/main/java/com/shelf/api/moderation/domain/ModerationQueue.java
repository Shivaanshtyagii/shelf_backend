package com.shelf.api.moderation.domain;

import com.shelf.api.listings.api.Listing;

import java.util.List;
import java.util.UUID;

public record ModerationQueue(List<Listing> listings, List<UUID> sellers, List<ModerationReport> reports,
                              List<ModerationAppeal> appeals) {}
