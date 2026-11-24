package com.shelf.api.accounts.web;

import com.shelf.api.accounts.api.Member;

import java.util.UUID;

public record ProfileResponse(UUID id, String email, String displayName, boolean verified, String role) {
    public static ProfileResponse from(Member row) {
        return new ProfileResponse(row.id(), row.email(), row.displayName(), row.verified(), row.role());
    }
}
