package com.shelf.api.assets.api;

import com.shelf.api.assets.api.Assets.Kind;

import java.util.UUID;

public record UploadedAssetResponse(UUID id, Kind kind, long bytes) {}
