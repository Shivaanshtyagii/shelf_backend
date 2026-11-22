package com.shelf.assets.internal.integration;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

public interface PrivateStorage {
    void put(String key, String contentType, Path file) throws IOException;
    void delete(String key) throws IOException;
    URI link(String key) throws IOException;
}
