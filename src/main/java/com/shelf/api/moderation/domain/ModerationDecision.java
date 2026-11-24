package com.shelf.api.moderation.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ModerationDecision(long id, UUID targetId, Integer revision, String outcome, String reason, OffsetDateTime createdAt) {}
