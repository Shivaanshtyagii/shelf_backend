package com.shelf.api.moderation.web;

import com.shelf.api.moderation.domain.ModerationActivity;

import java.util.List;

record ActivityResponse(List<ReportResponse> reports, List<AppealResponse> appeals) {
    static ActivityResponse from(ModerationActivity row) {
        return new ActivityResponse(row.reports().stream().map(ReportResponse::from).toList(),
                row.appeals().stream().map(AppealResponse::from).toList());
    }
}
