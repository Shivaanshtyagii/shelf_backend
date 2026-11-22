package com.shelf.accounts.internal;

import org.springframework.security.core.AuthenticatedPrincipal;

import java.io.Serializable;
import java.util.UUID;

// Spring Session stored this nested class by its binary name before the package split.
final class AccountService {
    private AccountService() {}

    record SessionIdentity(UUID id, int version) implements AuthenticatedPrincipal, Serializable {
        @Override
        public String getName() {
            return id.toString();
        }

        private Object readResolve() {
            return new com.shelf.accounts.internal.model.SessionIdentity(id, version);
        }
    }
}
