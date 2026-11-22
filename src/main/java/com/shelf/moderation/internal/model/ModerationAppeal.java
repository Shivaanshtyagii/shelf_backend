package com.shelf.moderation.internal.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ModerationAppeal(UUID id, long decisionId, UUID memberId, String reason, String status,
                               String response, OffsetDateTime createdAt) {}
