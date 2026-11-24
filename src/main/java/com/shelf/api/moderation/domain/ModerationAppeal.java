package com.shelf.api.moderation.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ModerationAppeal(UUID id, long decisionId, UUID memberId, String reason, String status,
                               String response, OffsetDateTime createdAt) {}
