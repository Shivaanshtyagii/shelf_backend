package com.shelf.api;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"shelf.secure-cookie=false", "shelf.assets.enabled=false"})
@ActiveProfiles("test")
public abstract class HttpTestSupport extends TestcontainersConfig {
    protected static final JsonMapper JSON = JsonMapper.builder().build();
    protected static final String PASSWORD = "pilot-password-42";

    @LocalServerPort protected int port;
    @Autowired protected JdbcClient db;
    @Autowired protected PasswordEncoder encoder;
    @MockitoBean protected JavaMailSender mail;

    protected UUID collegeA;
    protected UUID collegeB;
    protected String domainA;
    protected Browser guest;

    @BeforeEach
    void prepare() {
        collegeA = UUID.randomUUID();
        collegeB = UUID.randomUUID();
        domainA = "pilot-" + collegeA + ".example";
        college(collegeA, domainA);
        college(collegeB, "pilot-" + collegeB + ".example");
        guest = new Browser();
    }

    protected Browser moderator() throws Exception {
        var member = member("moderator", collegeA);
        db.sql("update accounts.member set role = 'MODERATOR' where id = :id")
                .param("id", member.id()).update();
        return member.browser();
    }

    protected void college(UUID id, String domain) {
        db.sql("insert into institutions.institution(id, name, email_domain) values (:id, :name, :domain)")
                .param("id", id).param("name", "Pilot College " + id).param("domain", domain).update();
    }

    protected SignedIn member(String name, UUID college) throws Exception {
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

    protected UUID listing(UUID owner, UUID college, String scope, String status) {
        var id = UUID.randomUUID();
        db.sql("""
                insert into listings.listing(id, owner_id, institution_id, scope, status, title, description, subject, published_at)
                values (:id, :owner, :college, :scope, :status, :title, 'Pilot listing', 'Pilot', now())
                """).param("id", id).param("owner", owner).param("college", college).param("scope", scope)
                .param("status", status).param("title", "Pilot " + id).update();
        return id;
    }

    protected String token(SimpleMailMessage message) {
        var match = Pattern.compile("token=([a-zA-Z0-9_-]+)").matcher(message.getText());
        assertThat(match.find()).isTrue();
        return match.group(1);
    }

    protected record SignedIn(UUID id, Browser browser) {}

    protected final class Browser {
        protected final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

        public HttpResponse<String> get(String path) throws Exception {
            return send(HttpRequest.newBuilder(uri(path)).GET());
        }

        public HttpResponse<String> post(String path, Map<String, ?> value) throws Exception {
            var csrf = get("/auth/csrf");
            assertThat(csrf.statusCode()).isEqualTo(200);
            JsonNode token = JSON.readTree(csrf.body());
            var request = HttpRequest.newBuilder(uri(path)).header(token.get("header").asText(), token.get("token").asText());
            if (value == null) return send(request.POST(HttpRequest.BodyPublishers.noBody()));
            return send(request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(value))));
        }

        protected HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        protected URI uri(String path) { return URI.create("http://127.0.0.1:" + port + "/api" + path); }
    }
}
