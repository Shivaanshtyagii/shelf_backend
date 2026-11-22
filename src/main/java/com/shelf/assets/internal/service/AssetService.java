package com.shelf.assets.internal.service;

import com.shelf.assets.Assets;
import com.shelf.assets.internal.config.AssetProperties;
import com.shelf.assets.internal.integration.MalwareScanner;
import com.shelf.assets.internal.integration.PrivateStorage;
import com.shelf.assets.internal.model.UploadedAsset;
import com.shelf.assets.internal.persistence.AssetRepository;
import com.shelf.common.ApiFault;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Service
@EnableConfigurationProperties(AssetProperties.class)
public class AssetService implements Assets {
    private static final long PDF_BYTES = 25_000_000;
    private static final long PHOTO_BYTES = 8_000_000;
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(AssetService.class);
    private final AssetProperties properties;
    private final AssetRepository files;
    private final PrivateStorage storage;
    private final MalwareScanner scanner;
    private final DocumentValidator validator;
    private final Semaphore uploadSlots = new Semaphore(2);

    AssetService(AssetProperties properties, AssetRepository files, PrivateStorage storage,
                 MalwareScanner scanner, DocumentValidator validator) {
        this.properties = properties;
        this.files = files;
        this.storage = storage;
        this.scanner = scanner;
        this.validator = validator;
    }

    @Override
    public boolean configured() { return properties.configured(); }

    public UploadedAsset upload(UUID owner, Kind kind, InputStream source, long size) throws IOException {
        if (!configured()) throw ApiFault.unavailable("Cloud uploads and downloads are disabled.");
        var limit = kind == Kind.PDF ? PDF_BYTES : PHOTO_BYTES;
        if (size <= 0 || size > limit) throw ApiFault.badRequest("The file is empty or too large.");
        if (!uploadSlots.tryAcquire()) throw ApiFault.unavailable("Uploads are busy. Try again in a moment.");

        Path temp = null;
        Path photo = null;
        try {
            temp = Files.createTempFile("shelf-upload-", ".incoming");
            try (var output = Files.newOutputStream(temp)) {
                source.transferTo(output);
            }
            scan(temp);
            if (kind == Kind.PHOTO) photo = Files.createTempFile("shelf-photo-", ".image");
            DocumentValidator.Validated validated;
            try {
                validated = validator.prepare(temp, photo, kind);
            } catch (IOException ex) {
                throw ApiFault.badRequest("The file could not be read as the selected type.");
            }
            var bytes = Files.size(validated.file());
            if (bytes == 0 || bytes > limit) throw ApiFault.badRequest("The prepared file exceeds the upload limit.");
            if (photo != null) scan(photo);
            var id = UUID.randomUUID();
            var key = "assets/" + id;
            if (!files.reserve(id, owner, key, validated.mediaType(), bytes, kind)) {
                throw ApiFault.conflict("The storage limit has been reached.");
            }
            try {
                storage.put(key, validated.mediaType(), validated.file());
            } catch (IOException ex) {
                files.finish(id, false);
                throw ApiFault.unavailable("Cloud storage is temporarily unavailable.");
            }
            files.finish(id, true);
            return new UploadedAsset(id, kind, bytes);
        } finally {
            deleteTemporary(photo);
            deleteTemporary(temp);
            uploadSlots.release();
        }
    }

    private void scan(Path file) {
        try {
            if (!scanner.clean(file)) throw ApiFault.badRequest("The file did not pass malware screening.");
        } catch (IOException ex) {
            throw ApiFault.unavailable("The malware scanner is temporarily unavailable.");
        }
    }

    private void deleteTemporary(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            LOG.warn("A temporary upload could not be deleted.");
        }
    }

    @Override
    public void claim(UUID id, UUID owner, Kind kind) {
        if (!configured()) throw ApiFault.unavailable("Cloud assets are disabled.");
        if (!files.claim(id, owner, kind)) {
            throw ApiFault.badRequest("Choose an available, scanned attachment of the correct type.");
        }
    }

    @Override
    public void release(UUID id, UUID owner) {
        if (id != null) files.release(id, owner);
    }

    @Override
    public void requireClean(UUID id, UUID owner, Kind kind) {
        if (!configured()) throw ApiFault.unavailable("Cloud assets are disabled.");
        if (id == null || files.byId(id).filter(asset -> asset.ownerId().equals(owner) && asset.kind() == kind).isEmpty()) {
            throw ApiFault.conflict("A clean, private attachment is required.");
        }
    }

    @Override
    public URI download(UUID id) {
        if (!configured()) throw ApiFault.unavailable("Cloud downloads are disabled.");
        var asset = files.byId(id).orElseThrow(() -> ApiFault.missing("The attachment is unavailable."));
        try {
            return storage.link(asset.storageKey());
        } catch (IOException ex) {
            throw ApiFault.unavailable("Cloud storage is temporarily unavailable.");
        }
    }

    @Scheduled(fixedDelayString = "PT15M")
    void purgeExpired() {
        if (!configured()) return;
        for (var asset : files.expired()) {
            try {
                storage.delete(asset.storageKey());
                files.forget(asset.id());
            } catch (IOException ex) {
                LOG.warn("Private asset cleanup will retry for asset {}", asset.id());
            }
        }
    }
}
