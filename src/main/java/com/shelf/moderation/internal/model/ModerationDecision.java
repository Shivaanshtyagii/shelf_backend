package com.shelf.moderation.internal.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ModerationDecision(long id, UUID targetId, Integer revision, String outcome, String reason, OffsetDateTime createdAt) {}
