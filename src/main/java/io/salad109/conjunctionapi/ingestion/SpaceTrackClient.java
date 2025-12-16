package io.salad109.conjunctionapi.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Service
public class SpaceTrackClient {

    private static final String SPACE_TRACK_LOGIN_URL = "/ajaxauth/login";
    // DECAY_DATE/null-val filters for active (non-decayed) satellites only
    private static final String SPACE_TRACK_QUERY_URL = "/basicspacedata/query/class/gp/DECAY_DATE/null-val/orderby/NORAD_CAT_ID/format/json";
    private static final String SPACE_TRACK_INCREMENTAL_QUERY_URL = "/basicspacedata/query/class/gp/DECAY_DATE/null-val/EPOCH/>%s/orderby/NORAD_CAT_ID/format/json";
    private static final Logger log = LoggerFactory.getLogger(SpaceTrackClient.class);
    private final RestClient restClient;
    @Value("${spacetrack.username}")
    private String username;
    @Value("${spacetrack.password}")
    private String password;

    public SpaceTrackClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Authenticate with Space-Track. Session cookies are stored automatically.
     */
    private void login() throws IOException {
        log.info("Authenticating with Space-Track...");
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("SPACETRACK_USERNAME not configured");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("SPACETRACK_PASSWORD not configured");
        }

        var loginResponse = restClient.post()
                .uri(SPACE_TRACK_LOGIN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("identity=" + URLEncoder.encode(username, StandardCharsets.UTF_8) +
                        "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8))
                .retrieve()
                .toBodilessEntity();

        if (!loginResponse.getStatusCode().is2xxSuccessful()) {
            throw new IOException("Login failed with status: " + loginResponse.getStatusCode());
        }

        log.info("Successfully authenticated with Space-Track");
    }

    /**
     * Fetch the entire GP catalog (current TLEs for all objects).
     */
    public List<OmmRecord> fetchFullCatalog() throws IOException {
        login();

        log.info("Fetching full GP catalog from Space-Track...");

        var response = restClient.get()
                .uri(SPACE_TRACK_QUERY_URL)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<OmmRecord>>() {
                });

        if (response.getStatusCode().isError()) {
            throw new IOException("Catalog fetch failed with status: " + response.getStatusCode());
        }
        if (response.getBody() == null) {
            throw new IOException("Catalog fetch returned no data");
        }
        log.info("Fetched {} objects from Space-Track", response.getBody().size());
        return response.getBody();
    }

    /**
     * Fetch TLEs updated since a given epoch.
     */
    public List<OmmRecord> fetchUpdatedSince(Instant since) throws IOException {
        login();

        String encodedEpoch = URLEncoder.encode(since.toString(), StandardCharsets.UTF_8);
        String query = String.format(SPACE_TRACK_INCREMENTAL_QUERY_URL, encodedEpoch);

        log.info("Fetching TLEs updated since {}", since);

        var response = restClient.get()
                .uri(query)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<OmmRecord>>() {
                });
        if (response.getStatusCode().isError()) {
            throw new IOException("Incremental fetch failed with status: " + response.getStatusCode());
        }
        if (response.getBody() == null) {
            throw new IOException("Incremental fetch returned no data");
        }
        log.info("Fetched {} updated objects", response.getBody().size());
        return response.getBody();
    }
}
