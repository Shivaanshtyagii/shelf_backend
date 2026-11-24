package com.shelf.api.assets.api;

import java.net.URI;
import java.util.UUID;

public interface Assets {
    enum Kind { PDF, PHOTO }

    boolean configured();
    void claim(UUID assetId, UUID owner, Kind kind);
    void release(UUID assetId, UUID owner);
    void requireClean(UUID assetId, UUID owner, Kind kind);
    URI download(UUID assetId);
}
