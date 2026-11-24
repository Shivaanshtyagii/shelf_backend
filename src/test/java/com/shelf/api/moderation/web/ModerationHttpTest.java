package com.shelf.api.moderation.web;

import com.shelf.api.HttpTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModerationHttpTest extends HttpTestSupport {
    @Test
    void takedownWritesHistoryAndTheListingOwnerCanAppeal() throws Exception {
        var seller = member("appealing-seller", collegeA);
        var moderator = moderator();
        var id = listing(seller.id(), null, "EXPLORE", "APPROVED");

        assertThat(moderator.post("/moderation/listings/" + id + "/takedown",
                Map.of("reason", "Needs a rights check")).statusCode()).isEqualTo(200);
        assertThat(guest.get("/explore?q=" + id).body()).doesNotContain(id.toString());
        var history = seller.browser().get("/moderation/listings/" + id + "/history");
        assertThat(history.statusCode()).isEqualTo(200);
        var decision = JSON.readTree(history.body()).get(0);
        assertThat(decision.get("outcome").asText()).isEqualTo("ARCHIVED");

        assertThat(seller.browser().post("/moderation/decisions/" + decision.get("id").asLong() + "/appeals",
                Map.of("reason", "I wrote this resource")).statusCode()).isEqualTo(200);
        assertThat(moderator.get("/moderation/queue").body()).contains("I wrote this resource");
    }
}
