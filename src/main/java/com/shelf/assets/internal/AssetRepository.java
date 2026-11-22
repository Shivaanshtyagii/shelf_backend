package com.shelf.assets.internal;

import com.shelf.assets.Assets.Kind;
import com.shelf.common.ApiFault;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class AssetRepository {
    private final JdbcClient db;
    private final AssetProperties properties;

    AssetRepository(JdbcClient db, AssetProperties properties) {
        this.db = db;
        this.properties = properties;
    }

    @Transactional
    public void reserve(UUID id, UUID owner, String key, String media, long size, Kind kind) {
        db.sql("select id from assets.quota where id for update").query(Boolean.class).single();
        var bytes = db.sql("""
                select coalesce(sum(byte_size), 0) as total,
                       coalesce(sum(byte_size) filter (where owner_id = :owner), 0) as owner
                from assets.file
                """).param("owner", owner).query(Usage.class).single();
        if (bytes.total() + size > properties.accountBytes() || bytes.owner() + size > properties.userBytes()) {
            throw ApiFault.conflict("The storage limit has been reached.");
        }
        db.sql("""
                insert into assets.file(id, owner_id, storage_key, media_type, byte_size, kind, state)
                values (:id, :owner, :key, :media, :size, :kind, 'UPLOADING')
                """).param("id", id).param("owner", owner).param("key", key)
                .param("media", media).param("size", size).param("kind", kind.name()).update();
    }

    void finish(UUID id, boolean uploaded) {
        db.sql("update assets.file set state = :state where id = :id and state = 'UPLOADING'")
                .param("state", uploaded ? "CLEAN" : "PURGING").param("id", id).update();
    }

    void claim(UUID id, UUID owner, Kind kind) {
        var changed = db.sql("""
                update assets.file set claimed = true
                where id = :id and owner_id = :owner and kind = :kind and state = 'CLEAN' and not claimed
                """).param("id", id).param("owner", owner).param("kind", kind.name()).update();
        if (changed != 1) throw ApiFault.badRequest("Choose an available, scanned attachment of the correct type.");
    }

    void release(UUID id, UUID owner) {
        db.sql("update assets.file set claimed = false, created_at = now() where id = :id and owner_id = :owner")
                .param("id", id).param("owner", owner).update();
    }

    Optional<Stored> byId(UUID id) {
        return db.sql("""
                select id, owner_id, storage_key, media_type, byte_size, kind
                from assets.file where id = :id and state = 'CLEAN'
                """).param("id", id).query(Stored.class).optional();
    }

    @Transactional
    public List<Stored> expired() {
        return db.sql("""
                update assets.file set state = 'PURGING' where id in (
                    select id from assets.file
                    where not claimed and (state = 'PURGING' or created_at < now() - interval '24 hours')
                    order by created_at limit 50 for update skip locked
                ) returning id, owner_id, storage_key, media_type, byte_size, kind
                """).query(Stored.class).list();
    }

    void forget(UUID id) {
        db.sql("delete from assets.file where id = :id and state = 'PURGING'").param("id", id).update();
    }

    record Stored(UUID id, UUID ownerId, String storageKey, String mediaType, long byteSize, Kind kind) {}
    record Usage(long total, long owner) {}
}
