package com.shelf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"shelf.secure-cookie=false", "shelf.assets.enabled=false"})
@EnabledIfEnvironmentVariable(named = "SHELF_PILOT_HTTP_TEST", matches = "true")
class PilotHttpTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "pilot-password-42";

    @LocalServerPort int port;
    @Autowired JdbcClient db;
    @Autowired PasswordEncoder encoder;
    @MockitoBean JavaMailSender mail;

    private UUID collegeA;
    private UUID collegeB;
    private String domainA;
    private Browser guest;

    @BeforeEach
    void prepare() {
        collegeA = UUID.randomUUID();
        collegeB = UUID.randomUUID();
        domainA = "pilot-" + collegeA + ".example";
        college(collegeA, domainA);
        college(collegeB, "pilot-" + collegeB + ".example");
        guest = new Browser();
    }

    @Test
    void verificationAndPasswordResetUseSingleUseLinksAndInvalidateEarlierSessions() throws Exception {
        var email = "student@" + domainA;
        assertThat(guest.post("/auth/register", Map.of("email", email, "displayName", "Student", "password", PASSWORD)).statusCode())
                .isEqualTo(202);
        var verification = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(verification.capture());
        var verificationToken = token(verification.getValue());

        assertThat(guest.post("/auth/login", Map.of("email", email, "password", PASSWORD)).statusCode()).isEqualTo(200);
        assertThat(guest.post("/institutions/" + collegeA + "/verify", null).statusCode()).isEqualTo(403);
        assertThat(guest.post("/auth/verify", Map.of("token", verificationToken)).statusCode()).isEqualTo(204);
        assertThat(guest.post("/auth/verify", Map.of("token", verificationToken)).statusCode()).isEqualTo(400);
        assertThat(guest.post("/institutions/" + collegeA + "/verify", null).statusCode()).isEqualTo(200);

        assertThat(guest.post("/auth/reset-request", Map.of("email", email)).statusCode()).isEqualTo(202);
        var messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, org.mockito.Mockito.times(2)).send(messages.capture());
        var resetToken = token(messages.getAllValues().getLast());
        var nextPassword = "another-password-43";
        assertThat(guest.post("/auth/reset", Map.of("token", resetToken, "password", nextPassword)).statusCode()).isEqualTo(204);
        assertThat(guest.get("/auth/me").statusCode()).isEqualTo(401);
        assertThat(guest.post("/auth/login", Map.of("email", email, "password", nextPassword)).statusCode()).isEqualTo(200);
    }

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

    @Test
    void disabledAssetsAndPaymentsCannotBeBypassedThroughPublishing() throws Exception {
        var seller = member("draft-seller", collegeA);
        db.sql("insert into listings.seller(member_id, status) values (:id, 'APPROVED')")
                .param("id", seller.id()).update();
        var created = seller.browser().post("/listings", Map.of("scope", "EXPLORE", "title", "Linear algebra",
                "subject", "Math", "description", "Worked examples", "priceMinor", 0, "revision", 0));
        assertThat(created.statusCode()).isEqualTo(200);
        var draft = JSON.readTree(created.body());
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        var id = draft.get("id").asText();
        assertThat(seller.browser().post("/listings/" + id + "/submit", Map.of("revision", 1)).statusCode()).isEqualTo(503);
        assertThat(seller.browser().post("/commerce/checkout", null).statusCode()).isEqualTo(503);
        assertThat(seller.browser().get("/capabilities").body()).contains("\"assets\":false");
        assertThat(guest.get("/explore?q=Linear+algebra").body()).doesNotContain(id);
    }

    private void college(UUID id, String domain) {
        db.sql("insert into institutions.institution(id, name, email_domain) values (:id, :name, :domain)")
                .param("id", id).param("name", "Pilot College " + id).param("domain", domain).update();
    }

    private SignedIn member(String name, UUID college) throws Exception {
        var id = UUID.randomUUID();
        var email = name + "-" + id + "@example.com";
        db.sql("""
                insert into accounts.member(id, email, display_name, password_hash, verified)
                values (:id, :email, :name, :password, true)
                """).param("id", id).param("email", email).param("name", name)
                .param("password", encoder.encode(PASSWORD)).update();
        db.sql("insert into institutions.affiliation(member_id, institution_id) values (:id, :college)")
                .param("id", id).param("college", college).update();
        var browser = new Browser();
        assertThat(browser.post("/auth/login", Map.of("email", email, "password", PASSWORD)).statusCode()).isEqualTo(200);
        return new SignedIn(id, browser);
    }

    private UUID listing(UUID owner, UUID college, String scope, String status) {
        var id = UUID.randomUUID();
        db.sql("""
                insert into listings.listing(id, owner_id, institution_id, scope, status, title, description, subject, published_at)
                values (:id, :owner, :college, :scope, :status, :title, 'Pilot listing', 'Pilot', now())
                """).param("id", id).param("owner", owner).param("college", college).param("scope", scope)
                .param("status", status).param("title", "Pilot " + id).update();
        return id;
    }

    private String token(SimpleMailMessage message) {
        var match = Pattern.compile("token=([a-zA-Z0-9_-]+)").matcher(message.getText());
        assertThat(match.find()).isTrue();
        return match.group(1);
    }

    private record SignedIn(UUID id, Browser browser) {}

    private final class Browser {
        private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

        HttpResponse<String> get(String path) throws Exception {
            return send(HttpRequest.newBuilder(uri(path)).GET());
        }

        HttpResponse<String> post(String path, Map<String, ?> value) throws Exception {
            var csrf = get("/auth/csrf");
            assertThat(csrf.statusCode()).isEqualTo(200);
            JsonNode token = JSON.readTree(csrf.body());
            var request = HttpRequest.newBuilder(uri(path)).header(token.get("header").asText(), token.get("token").asText());
            if (value == null) return send(request.POST(HttpRequest.BodyPublishers.noBody()));
            return send(request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(value))));
        }

        private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + "/api" + path); }
    }
}
