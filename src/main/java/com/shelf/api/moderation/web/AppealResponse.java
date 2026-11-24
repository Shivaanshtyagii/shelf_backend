package com.shelf.api.moderation.web;

import com.shelf.api.moderation.domain.ModerationAppeal;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppealResponse(UUID id, long decisionId, UUID memberId, String reason, String status, String response,
        OffsetDateTime createdAt) {
    public static AppealResponse from(ModerationAppeal row) {
        return new AppealResponse(row.id(), row.decisionId(), row.memberId(), row.reason(), row.status(),
                row.response(), row.createdAt());
    }
}
