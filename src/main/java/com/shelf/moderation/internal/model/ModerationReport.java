package com.shelf.moderation.internal.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ModerationReport(UUID id, UUID listingId, UUID reporterId, String reason, String status,
                               String response, OffsetDateTime createdAt) {}
