package com.shelf.assets.internal;

import java.net.URI;
import java.nio.file.Path;

interface PrivateStorage {
    void put(String key, String contentType, Path file);
    void delete(String key);
    URI link(String key);
}
