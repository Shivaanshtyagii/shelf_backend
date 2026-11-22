package com.shelf.institutions;

import java.util.Optional;
import java.util.UUID;

public interface Affiliations {
    Optional<UUID> verifiedInstitution(UUID memberId);
    UUID requireInstitution(UUID memberId);
}
