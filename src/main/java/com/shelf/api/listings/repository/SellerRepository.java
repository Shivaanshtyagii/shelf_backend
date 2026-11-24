package com.shelf.api.listings.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class SellerRepository {
    private final JdbcClient db;

    SellerRepository(JdbcClient db) {
        this.db = db;
    }

    public String sellerStatus(UUID memberId) {
        return db.sql("select status from listings.seller where member_id = :member")
                .param("member", memberId).query(String.class).optional().orElse("UNREGISTERED");
    }

    public void applyToSell(UUID memberId) {
        db.sql("""
                insert into listings.seller(member_id, status) values (:member, 'PENDING')
                on conflict (member_id) do update set status = 'PENDING', updated_at = now()
                where listings.seller.status = 'REJECTED'
                """).param("member", memberId).update();
    }

    public List<UUID> sellerQueue() {
        return db.sql("select member_id from listings.seller where status = 'PENDING' order by updated_at limit 100")
                .query(UUID.class).list();
    }

    public boolean decideSeller(UUID id, boolean approved) {
        return db.sql("""
                update listings.seller set status = :status, updated_at = now() where member_id = :id and status = 'PENDING'
                """).param("id", id).param("status", approved ? "APPROVED" : "REJECTED").update() == 1;
    }
}
