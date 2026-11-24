package com.shelf.api.accounts.domain;

import java.util.UUID;

public record AccountCredentials(UUID id, String passwordHash, boolean verified, int credentialVersion) {}
