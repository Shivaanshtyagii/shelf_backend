package com.shelf.institutions.internal.model;

import java.util.UUID;

public record DirectoryRequest(UUID id, UUID memberId, Kind kind, UUID institutionId, String name,
                               String detail, String status) {
    public enum Kind { INSTITUTION, COURSE, AFFILIATION }
}
