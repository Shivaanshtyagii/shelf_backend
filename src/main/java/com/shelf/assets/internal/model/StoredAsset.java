package com.shelf.assets.internal.model;

import com.shelf.assets.Assets.Kind;

import java.util.UUID;

public record StoredAsset(UUID id, UUID ownerId, String storageKey, String mediaType, long byteSize, Kind kind) {}
