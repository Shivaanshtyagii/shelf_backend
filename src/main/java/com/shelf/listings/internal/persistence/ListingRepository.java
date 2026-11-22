package com.shelf.listings.internal.persistence;

import com.shelf.listings.internal.model.ListingSearch;
import com.shelf.listings.Listing;
import com.shelf.listings.Listing.Scope;
import com.shelf.listings.Listing.Status;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ListingRepository {
    private static final String COLUMNS = """
            id, owner_id, scope, institution_id, title, description, subject, price_minor, asset_id, status, revision, published_at
            """;
    private final JdbcClient db;

    ListingRepository(JdbcClient db) { this.db = db; }

    public List<Listing> discover(Scope scope, UUID institution, ListingSearch query) {
        var term = query.q().strip();
        var subject = query.subject().strip().toLowerCase(Locale.ROOT);
        var order = switch (query.sort()) {
            case PRICE_LOW -> "price_minor, published_at desc, id";
            case PRICE_HIGH -> "price_minor desc, published_at desc, id";
            case NEWEST -> "published_at desc, id";
            case RELEVANCE -> """
                    ts_rank(search_document, websearch_to_tsquery('simple', :term)) desc,
                    similarity(title, :term) desc, published_at desc, id
                    """;
        };
        return db.sql("select " + COLUMNS + """
                from listings.listing
                where status = 'APPROVED' and scope = :scope
                  and institution_id is not distinct from cast(:institution as uuid)
                  and (:subject = '' or lower(subject) = :subject)
                  and (:term = '' or search_document @@ websearch_to_tsquery('simple', :term)
                       or title % :term or title ilike :prefix escape '!')
                order by
                """ + order + " limit :limit offset :offset")
                .param("scope", scope.name()).param("institution", institution)
                .param("subject", subject).param("term", term)
                .param("prefix", term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%")
                .param("limit", query.size() + 1).param("offset", query.page() * query.size())
                .query(Listing.class).list();
    }

    public Optional<Listing> find(UUID id, boolean lock) {
        return db.sql("select " + COLUMNS + " from listings.listing where id = :id" + (lock ? " for update" : ""))
                .param("id", id).query(Listing.class).optional();
    }

    public List<Listing> mine(UUID owner) {
        return db.sql("select " + COLUMNS + " from listings.listing where owner_id = :owner order by created_at desc limit 100")
                .param("owner", owner).query(Listing.class).list();
    }

    public List<Listing> queue() {
        return db.sql("select " + COLUMNS + " from listings.listing where status = 'PENDING' order by created_at limit 100")
                .query(Listing.class).list();
    }

    public void insert(Listing listing) {
        values(db.sql("""
                insert into listings.listing(id, owner_id, scope, institution_id, title, description, subject, price_minor, asset_id)
                values (:id, :owner, :scope, :college, :title, :description, :subject, :price, :asset)
                """), listing).update();
    }

    public void replace(Listing listing) {
        values(db.sql("""
                update listings.listing set scope = :scope, institution_id = :college, title = :title,
                    description = :description, subject = :subject, price_minor = :price, asset_id = :asset,
                    status = 'DRAFT', revision = revision + 1
                where id = :id and owner_id = :owner
                """), listing).update();
    }

    public Listing transition(UUID id, Status status) {
        return db.sql("""
                update listings.listing set status = :status,
                    published_at = case when :status = 'APPROVED' then now() else published_at end
                where id = :id returning
                """ + COLUMNS).param("id", id).param("status", status.name()).query(Listing.class).single();
    }

    private JdbcClient.StatementSpec values(JdbcClient.StatementSpec statement, Listing listing) {
        return statement.param("id", listing.id()).param("owner", listing.ownerId()).param("scope", listing.scope().name())
                .param("college", listing.institutionId()).param("title", listing.title()).param("description", listing.description())
                .param("subject", listing.subject()).param("price", listing.priceMinor()).param("asset", listing.assetId());
    }
}
