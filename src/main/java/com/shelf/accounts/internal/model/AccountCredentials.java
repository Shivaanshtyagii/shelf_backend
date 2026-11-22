package com.shelf.accounts.internal.model;

import java.util.UUID;

public record AccountCredentials(UUID id, String passwordHash, boolean verified, int credentialVersion) {}
