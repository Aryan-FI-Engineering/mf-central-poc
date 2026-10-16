package com.fundsindia.mfcpoc.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fundsindia.mfcpoc.config.MfCentralProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * OAuth2 password-grant token for MFCentral, cached until shortly before expiry (MFC issues ~30-day tokens).
 * Synchronized so concurrent callers trigger one token request, not many.
 */
@Component
public class AccessTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(AccessTokenProvider.class);
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(5);

    private final RestClient restClient;
    private final MfCentralProperties properties;
    private final Clock clock;

    private AccessToken cached;

    public AccessTokenProvider(RestClient mfCentralRestClient, MfCentralProperties properties, Clock clock) {
        this.restClient = mfCentralRestClient;
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized String token() {
        Instant now = clock.instant();
        if (cached == null || !now.isBefore(cached.expiresAt().minus(EXPIRY_MARGIN))) {
            cached = fetch(now);
        }
        return cached.value();
    }

    /** Called after MFC rejects a token with 401, so the next call fetches a fresh one. */
    public synchronized void invalidate() {
        cached = null;
    }

    private AccessToken fetch(Instant now) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("username", properties.username());
        form.add("password", properties.password());
        form.add("grant_type", "password");

        TokenResponse response = restClient.post()
                .uri(properties.tokenPath())
                .headers(h -> h.setBasicAuth(properties.clientId(), properties.clientSecret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new MfCentralException("token", 200, "Token response did not contain an access_token");
        }
        log.info("Fetched MFCentral access token, expires in {}s", response.expiresIn());
        return new AccessToken(response.accessToken(), now.plusSeconds(response.expiresIn()));
    }

    private record AccessToken(String value, Instant expiresAt) {}

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn) {}
}
