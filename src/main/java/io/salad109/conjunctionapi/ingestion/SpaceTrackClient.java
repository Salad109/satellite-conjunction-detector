package io.salad109.conjunctionapi.ingestion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Service
public class SpaceTrackClient {

    private static final String SPACE_TRACK_BASE_URL = "https://www.space-track.org";
    private static final Logger log = LoggerFactory.getLogger(SpaceTrackClient.class);
    private final BasicCookieStore cookieStore = new BasicCookieStore();
    private final ObjectMapper objectMapper;
    private CloseableHttpClient httpClient;
    private Instant lastLogin;
    @Value("${spacetrack.username}")
    private String username;
    @Value("${spacetrack.password}")
    private String password;

    public SpaceTrackClient() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @PostConstruct
    public void init() {
        this.httpClient = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .build();
    }

    @PreDestroy
    public void cleanup() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    /**
     * Authenticate with Space-Track. Session cookies are stored automatically.
     */
    public void login() throws IOException {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("SPACETRACK_USERNAME not configured");
        }

        String loginUrl = SPACE_TRACK_BASE_URL + "/ajaxauth/login";
        HttpPost post = new HttpPost(loginUrl);

        String body = "identity=" + URLEncoder.encode(username, StandardCharsets.UTF_8) +
                "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        post.setEntity(new StringEntity(body, ContentType.APPLICATION_FORM_URLENCODED));

        httpClient.execute(post, response -> {
            int status = response.getCode();
            if (status != 200) {
                throw new IOException("Login failed with status: " + status);
            }
            log.info("Successfully authenticated with Space-Track");
            lastLogin = Instant.now();
            return null;
        });
    }

    /**
     * Fetch the entire GP catalog (current TLEs for all objects).
     * Returns OMM (Orbit Mean-Elements Message) format as JSON.
     */
    public List<OmmRecord> fetchFullCatalog() throws IOException {
        ensureLoggedIn();

        // GP class gives us the latest TLE for each object
        // DECAY_DATE/null-val filters for active (non-decayed) satellites only
        String query = SPACE_TRACK_BASE_URL + "/basicspacedata/query/class/gp/DECAY_DATE/null-val/orderby/NORAD_CAT_ID/format/json";

        log.info("Fetching full GP catalog from Space-Track...");
        HttpGet get = new HttpGet(query);

        return httpClient.execute(get, response -> {
            int status = response.getCode();
            if (status == 401) {
                // Session expired, re-login and retry
                login();
                return fetchFullCatalog();
            }
            if (status != 200) {
                throw new IOException("Catalog fetch failed with status: " + status);
            }

            String json = EntityUtils.toString(response.getEntity());
            List<OmmRecord> records = objectMapper.readValue(json, new TypeReference<>() {
            });
            log.info("Fetched {} objects from Space-Track", records.size());
            return records;
        });
    }

    /**
     * Fetch TLEs updated since a given epoch.
     * Useful for incremental sync.
     */
    public List<OmmRecord> fetchUpdatedSince(Instant since) throws IOException {
        ensureLoggedIn();

        String epochFilter = since.toString().replace(":", "%3A");
        String query = SPACE_TRACK_BASE_URL + "/basicspacedata/query/class/gp" +
                "/DECAY_DATE/null-val" +
                "/EPOCH/%3E" + epochFilter +
                "/orderby/NORAD_CAT_ID/format/json";

        log.info("Fetching TLEs updated since {}", since);
        HttpGet get = new HttpGet(query);

        return httpClient.execute(get, response -> {
            int status = response.getCode();
            if (status == 401) {
                login();
                return fetchUpdatedSince(since);
            }
            if (status != 200) {
                throw new IOException("Incremental fetch failed with status: " + status);
            }

            String json = EntityUtils.toString(response.getEntity());
            List<OmmRecord> records = objectMapper.readValue(json, new TypeReference<>() {
            });
            log.info("Fetched {} updated objects", records.size());
            return records;
        });
    }

    private void ensureLoggedIn() throws IOException {
        // Re-login if never logged in or session older than 1 hour
        if (lastLogin == null || lastLogin.plusSeconds(3600).isBefore(Instant.now())) {
            login();
        }
    }
}
