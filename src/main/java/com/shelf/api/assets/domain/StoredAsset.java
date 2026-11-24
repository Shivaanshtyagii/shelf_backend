package com.shelf.api.assets.domain;

import com.shelf.api.assets.api.Assets.Kind;

import java.util.UUID;

public record StoredAsset(UUID id, UUID ownerId, String storageKey, String mediaType, long byteSize, Kind kind) {}
