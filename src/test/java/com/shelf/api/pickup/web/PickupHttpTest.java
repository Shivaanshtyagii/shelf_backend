package com.shelf.api.pickup.web;

import com.shelf.api.HttpTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PickupHttpTest extends HttpTestSupport {
    @Test
    void searchAndActionsOnlyExposePublishedListingsWithinTheirAuthorizedScope() throws Exception {
        var seller = member("seller", collegeA);
        var buyer = member("buyer", collegeA);
        var outsider = member("outsider", collegeB);
        var ownNote = listing(seller.id(), collegeA, "CAMPUS_MATERIALS", "APPROVED");
        var hiddenDraft = listing(seller.id(), collegeA, "CAMPUS_MATERIALS", "DRAFT");
        var otherNote = listing(outsider.id(), collegeB, "CAMPUS_MATERIALS", "APPROVED");
        var nationwide = listing(seller.id(), null, "EXPLORE", "APPROVED");
        var goods = listing(seller.id(), collegeA, "CAMPUS_MARKETPLACE", "APPROVED");

        assertThat(guest.get("/campus?scope=CAMPUS_MATERIALS").statusCode()).isEqualTo(401);
        var nationalResult = guest.get("/explore?q=" + nationwide);
        assertThat(nationalResult.statusCode()).isEqualTo(200);
        assertThat(nationalResult.body()).contains(nationwide.toString()).doesNotContain(ownNote.toString());
        var campusResult = buyer.browser().get("/campus?scope=CAMPUS_MATERIALS&q=pilot");
        assertThat(campusResult.statusCode()).isEqualTo(200);
        assertThat(campusResult.body()).contains(ownNote.toString())
                .doesNotContain(otherNote.toString(), hiddenDraft.toString(), goods.toString());
        assertThat(outsider.browser().get("/listings/" + ownNote + "/attachment").statusCode()).isEqualTo(404);
        assertThat(buyer.browser().get("/moderation/queue").statusCode()).isEqualTo(403);
        assertThat(outsider.browser().post("/pickup", Map.of("listingId", goods)).statusCode()).isEqualTo(404);
        var pickup = buyer.browser().post("/pickup", Map.of("listingId", goods));
        assertThat(pickup.statusCode()).isEqualTo(200);
        var id = JSON.readTree(pickup.body()).get("id").asText();
        assertThat(buyer.browser().post("/pickup/" + id, Map.of("status", "COLLECTED")).statusCode()).isEqualTo(409);
        assertThat(seller.browser().post("/pickup/" + id, Map.of("status", "ACCEPTED", "meetingLocation",
                "Library entrance", "meetingTime", "2090-01-01T06:00:00Z")).statusCode()).isEqualTo(200);
        assertThat(outsider.browser().get("/pickup").body()).isEqualTo("[]");
    }
}
