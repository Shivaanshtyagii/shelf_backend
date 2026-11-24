package com.shelf.api.moderation.web;

import com.shelf.api.moderation.domain.ModerationReport;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReportResponse(UUID id, UUID listingId, UUID reporterId, String reason, String status, String response,
        OffsetDateTime createdAt) {
    public static ReportResponse from(ModerationReport row) {
        return new ReportResponse(row.id(), row.listingId(), row.reporterId(), row.reason(), row.status(),
                row.response(), row.createdAt());
    }
}
