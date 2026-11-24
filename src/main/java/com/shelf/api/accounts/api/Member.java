package com.shelf.api.accounts.api;

import java.util.UUID;

public record Member(UUID id, String email, String displayName, boolean verified, String role) {
    public boolean moderator() { return "MODERATOR".equals(role); }
}
