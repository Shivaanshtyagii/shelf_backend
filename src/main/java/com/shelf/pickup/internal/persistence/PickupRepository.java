package com.shelf.pickup.internal.persistence;

import com.shelf.pickup.internal.model.PickupRequest;
import com.shelf.pickup.internal.model.PickupRequest.Status;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PickupRepository {
    private static final String DETAILS = """
            id, listing_id, listing_title, institution_id, buyer_id, seller_id, status, meeting_location, meeting_time, created_at
            """;
    private final JdbcClient db;

    PickupRepository(JdbcClient db) { this.db = db; }

    public Optional<PickupRequest> requestUnreservedItem(UUID id, UUID listing, String title, UUID college, UUID buyer, UUID seller) {
        return db.sql("""
                insert into pickup.request(id, listing_id, listing_title, institution_id, buyer_id, seller_id, status)
                select :id, :listing, :title, :college, :buyer, :seller, 'REQUESTED'
                where not exists (
                    select 1 from pickup.request where listing_id = :listing and status in ('ACCEPTED', 'COLLECTED')
                )
                returning
                """ + DETAILS).param("id", id).param("listing", listing).param("title", title)
                .param("college", college).param("buyer", buyer).param("seller", seller)
                .query(PickupRequest.class).optional();
    }

    public List<PickupRequest> forParticipant(UUID member, UUID college) {
        return db.sql("select " + DETAILS + """
                from pickup.request where (buyer_id = :member or seller_id = :member) and institution_id = :campus
                order by created_at desc limit 100
                """).param("member", member).param("campus", college).query(PickupRequest.class).list();
    }

    public Optional<PickupRequest> lockForParticipant(UUID id, UUID member, UUID college) {
        return db.sql("select " + DETAILS + """
                from pickup.request where id = :id and institution_id = :campus
                    and (buyer_id = :member or seller_id = :member) for update
                """).param("id", id).param("campus", college).param("member", member)
                .query(PickupRequest.class).optional();
    }

    public PickupRequest changeStatus(UUID id, Status status, String location, OffsetDateTime time) {
        return db.sql("""
                update pickup.request set status = :status, meeting_location = :location, meeting_time = :time
                where id = :id returning
                """ + DETAILS).param("status", status.name()).param("location", location)
                .param("time", time).param("id", id).query(PickupRequest.class).single();
    }
}
