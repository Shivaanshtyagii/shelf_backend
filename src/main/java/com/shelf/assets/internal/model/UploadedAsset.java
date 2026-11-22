package com.shelf.assets.internal.model;

import com.shelf.assets.Assets.Kind;

import java.util.UUID;

public record UploadedAsset(UUID id, Kind kind, long bytes) {}
