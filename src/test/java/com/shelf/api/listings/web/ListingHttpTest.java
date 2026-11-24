package com.shelf.api.listings.web;

import com.shelf.api.HttpTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ListingHttpTest extends HttpTestSupport {
    @Test
    void disabledAssetsAndPaymentsCannotBeBypassedThroughPublishing() throws Exception {
        var seller = member("draft-seller", collegeA);
        assertThat(seller.browser().get("/seller").body()).contains("UNREGISTERED");
        assertThat(seller.browser().post("/seller", null).body()).contains("PENDING");
        var moderator = moderator();
        assertThat(moderator.get("/moderation/queue").body()).contains(seller.id().toString());
        assertThat(moderator.post("/moderation/sellers/" + seller.id(),
                Map.of("accepted", true, "reason", "Application reviewed")).statusCode()).isEqualTo(200);
        assertThat(seller.browser().get("/seller").body()).contains("APPROVED");
        var created = seller.browser().post("/listings", Map.of("scope", "EXPLORE", "title", "Linear algebra",
                "subject", "Math", "description", "Worked examples", "priceMinor", 0, "revision", 0));
        assertThat(created.statusCode()).isEqualTo(200);
        var draft = JSON.readTree(created.body());
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        var id = draft.get("id").asText();
        assertThat(seller.browser().post("/listings/" + id + "/submit", Map.of("revision", 1)).statusCode()).isEqualTo(503);
        assertThat(seller.browser().post("/commerce/checkout", null).statusCode()).isEqualTo(503);
        assertThat(guest.get("/capabilities").body()).contains("\"assets\":false", "\"digitalCommerce\":false");
        assertThat(guest.get("/explore?q=Linear+algebra").body()).doesNotContain(id);
    }
}
