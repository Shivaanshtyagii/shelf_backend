package com.shelf.api.moderation.web;

import com.shelf.api.moderation.domain.ModerationDecision;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DecisionResponse(long id, UUID targetId, Integer revision, String outcome, String reason,
        OffsetDateTime createdAt) {
    public static DecisionResponse from(ModerationDecision row) {
        return new DecisionResponse(row.id(), row.targetId(), row.revision(), row.outcome(), row.reason(),
                row.createdAt());
    }
}
