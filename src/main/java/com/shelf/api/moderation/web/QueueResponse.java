package com.shelf.api.moderation.web;

import com.shelf.api.listings.api.ListingResponse;
import com.shelf.api.moderation.domain.ModerationQueue;

import java.util.List;
import java.util.UUID;

record QueueResponse(List<ListingResponse> listings, List<UUID> sellers, List<ReportResponse> reports,
        List<AppealResponse> appeals) {
    static QueueResponse from(ModerationQueue row) {
        return new QueueResponse(row.listings().stream().map(ListingResponse::from).toList(), row.sellers(),
                row.reports().stream().map(ReportResponse::from).toList(),
                row.appeals().stream().map(AppealResponse::from).toList());
    }
}
