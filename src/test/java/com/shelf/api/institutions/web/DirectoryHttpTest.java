package com.shelf.api.institutions.web;

import com.shelf.api.HttpTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DirectoryHttpTest extends HttpTestSupport {
    @Test
    void directoryReviewRollsBackWhenAnOfficialCourseCodeIsMissing() throws Exception {
        var student = member("requester", collegeA).browser();
        var moderator = moderator();
        var submitted = student.post("/institutions/requests",
                Map.of("kind", "COURSE", "institutionId", collegeA, "name", "Linear Algebra"));
        assertThat(submitted.statusCode()).isEqualTo(200);
        var id = JSON.readTree(submitted.body()).get("id").asText();
        var review = "/institutions/review/" + id;

        assertThat(moderator.post(review, Map.of("approved", true)).statusCode()).isEqualTo(400);
        assertThat(moderator.get("/institutions/review").body()).contains(id);
        assertThat(moderator.post(review, Map.of("approved", true, "verifiedValue", "MATH101")).statusCode()).isEqualTo(200);
        assertThat(guest.get("/institutions/" + collegeA + "/courses").body()).contains("MATH101", "Linear Algebra");
    }
}
