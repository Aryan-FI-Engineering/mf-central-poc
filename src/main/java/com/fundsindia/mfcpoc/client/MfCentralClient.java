package com.fundsindia.mfcpoc.client;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundsindia.mfcpoc.config.MfCentralProperties;
import com.fundsindia.mfcpoc.crypto.PayloadCipher;
import com.fundsindia.mfcpoc.crypto.PayloadSigner;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls {@code /api/client/V1/<endpoint>} using MFCentral's envelope protocol:
 * <ol>
 *   <li>request JSON → AES encrypt → Base64</li>
 *   <li>detached JWS over the ciphertext</li>
 *   <li>POST {@code {"request": ..., "signature": ...}}</li>
 *   <li>verify the response signature over the response ciphertext, then decrypt</li>
 * </ol>
 * Never retries on timeout: {@code newCasRequest} sends the investor an OTP, so a blind retry is not harmless.
 * It retries once on 401, because MFC rejected the token before doing anything.
 */
@Component
public class MfCentralClient {

    private static final Logger log = LoggerFactory.getLogger(MfCentralClient.class);
    private static final String API_PATH = "/api/client/V1/{endpoint}";

    private final RestClient restClient;
    private final AccessTokenProvider tokens;
    private final PayloadCipher cipher;
    private final PayloadSigner signer;
    private final ObjectMapper objectMapper;
    private final String clientId;

    public MfCentralClient(
            RestClient mfCentralRestClient,
            AccessTokenProvider tokens,
            PayloadCipher cipher,
            PayloadSigner signer,
            ObjectMapper objectMapper,
            MfCentralProperties properties) {
        this.restClient = mfCentralRestClient;
        this.tokens = tokens;
        this.cipher = cipher;
        this.signer = signer;
        this.objectMapper = objectMapper;
        this.clientId = properties.clientId();
    }

    /** Returns the decrypted response payload for a 2xx; throws {@link MfCentralException} otherwise. */
    public JsonNode call(String endpoint, Map<String, ?> request) {
        String encrypted = cipher.encrypt(toJson(request));
        Map<String, String> envelope = Map.of("request", encrypted, "signature", signer.sign(encrypted));

        Reply reply = post(endpoint, envelope, tokens.token());
        if (reply.status() == 401) {
            tokens.invalidate();
            reply = post(endpoint, envelope, tokens.token());
        }

        JsonNode payload = open(endpoint, reply);
        if (reply.status() < 200 || reply.status() >= 300) {
            throw new MfCentralException(endpoint, reply.status(), payload.path("errors"));
        }
        return payload;
    }

    private Reply post(String endpoint, Map<String, String> envelope, String token) {
        long started = System.nanoTime();
        Reply reply = restClient.post()
                .uri(API_PATH, endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header("ClientId", clientId)
                .headers(h -> h.setBearerAuth(token))
                .body(envelope)
                .exchange((req, res) -> new Reply(res.getStatusCode().value(), new String(res.getBody().readAllBytes(), UTF_8)));
        log.info("MFCentral {} -> HTTP {} in {} ms", endpoint, reply.status(), (System.nanoTime() - started) / 1_000_000);
        return reply;
    }

    /** Unwraps a signed+encrypted envelope. Error replies may arrive as plain JSON and are returned as-is. */
    private JsonNode open(String endpoint, Reply reply) {
        if (reply.body().isBlank()) {
            return objectMapper.createObjectNode();
        }
        JsonNode body = parse(endpoint, reply.status(), reply.body());
        String encrypted = body.path("response").asText("");
        String signature = body.path("signature").asText("");
        if (encrypted.isEmpty()) {
            return body;
        }
        if (!signer.verify(encrypted, signature)) {
            throw new MfCentralException(endpoint, reply.status(), "response signature verification failed");
        }
        String decrypted;
        try {
            decrypted = cipher.decrypt(encrypted);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Signed but not decryptable: either a plaintext error or a different encoding/key. Hand the
            // raw text to the caller so the user can see what MFC said; keep it out of logs (may hold PII).
            throw new MfCentralException(endpoint, reply.status(), "response could not be decrypted: " + e.getMessage(),
                    objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                            .put("code", String.valueOf(reply.status()))
                            .put("rawResponse", abbreviate(encrypted))));
        }
        return parse(endpoint, reply.status(), decrypted);
    }

    private static String abbreviate(String text) {
        return text.length() > 500 ? text.substring(0, 500) + "…" : text;
    }

    private JsonNode parse(String endpoint, int status, String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new MfCentralException(endpoint, status, "response is not JSON");
        }
    }

    private String toJson(Map<String, ?> request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Request is not serialisable", e);
        }
    }

    private record Reply(int status, String body) {}
}
