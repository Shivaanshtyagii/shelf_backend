package com.shelf.api.institutions.web;

import com.shelf.api.institutions.domain.DirectoryRequest.Kind;
import com.shelf.api.institutions.domain.DirectoryRequest;

import java.util.UUID;

public record DirectoryRequestResponse(UUID id, UUID memberId, Kind kind, UUID institutionId, String name,
                                       String detail, String status) {
    public static DirectoryRequestResponse from(DirectoryRequest row) {
        return new DirectoryRequestResponse(row.id(), row.memberId(), row.kind(), row.institutionId(), row.name(),
                row.detail(), row.status());
    }
}
