package com.shelf.api.accounts.domain;

import org.springframework.security.core.AuthenticatedPrincipal;

import java.io.Serializable;
import java.util.UUID;

public record SessionIdentity(UUID id, int version) implements AuthenticatedPrincipal, Serializable {
    @Override
    public String getName() {
        return id.toString();
    }
}
