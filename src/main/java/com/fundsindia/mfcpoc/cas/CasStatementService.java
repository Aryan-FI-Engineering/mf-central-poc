package com.fundsindia.mfcpoc.cas;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundsindia.mfcpoc.client.MfCentralClient;
import com.fundsindia.mfcpoc.client.MfCentralException;
import com.fundsindia.mfcpoc.config.MfCentralProperties;
import com.fundsindia.mfcpoc.crypto.PortalDataCipher;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static java.nio.charset.StandardCharsets.UTF_8;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * The calling-application half of MFCentral's QR-based CAS flow. OTP entry, AMC selection and QR
 * generation happen on MFC's hosted portal between {@link #start} and {@link #fetchStatement}.
 */
@Service
public class CasStatementService {

    private static final Logger log = LoggerFactory.getLogger(CasStatementService.class);
    private static final DateTimeFormatter MFC_DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    // MFC's portal handoff dates use slashes (dd/MMM/yyyy), unlike the API body which uses dashes.
    private static final DateTimeFormatter PORTAL_DATE = DateTimeFormatter.ofPattern("dd/MMM/yyyy", Locale.ENGLISH);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    // When no range is chosen, mirror FundsIndia's ecas/otp backend: full history to today.
    private static final LocalDate PORTAL_DEFAULT_FROM = LocalDate.of(1990, 1, 1);

    private final MfCentralClient mfCentral;
    private final CasRequestStore store;
    private final MfCentralProperties properties;
    private final PortalDataCipher portalDataCipher;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public CasStatementService(MfCentralClient mfCentral, CasRequestStore store, MfCentralProperties properties,
                               PortalDataCipher portalDataCipher, ObjectMapper objectMapper, Clock clock) {
        this.mfCentral = mfCentral;
        this.store = store;
        this.properties = properties;
        this.portalDataCipher = portalDataCipher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Calls newCasRequest, which makes MFC send the investor an OTP. Not retried: a retry sends a second OTP. */
    public StartedCasRequest start(CasStatementCommand command) {
        String clientRefNo = newClientRefNo();
        MDC.put("clientRefNo", clientRefNo);
        try {
            LocalDate from = command.detailed() ? command.fromDate() : PORTAL_DEFAULT_FROM;
            LocalDate to = command.detailed() ? command.toDate() : LocalDate.ofInstant(clock.instant(), IST);

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("clientRefNo", clientRefNo);
            request.put("reqId", "");
            request.put("pan", command.pan());
            request.put("pekrn", "");
            request.put("email", command.email());
            // Mobile and dates must match the portal handoff exactly, or generate-qr fails with
            // "Kindly follow the same details for the complete transaction". So: always send the range
            // (Summary vs Transactions is chosen on the portal, not by omitting dates) and bare mobile.
            request.put("mobile", localMobile(command.mobile()));
            request.put("fromDate", mfcDate(from));
            request.put("toDate", mfcDate(to));
            request.put("redirectUrl", properties.redirectUrl());

            JsonNode response = mfCentral.call("newCasRequest", request);
            String reqId = response.path("reqId").asText("");
            if (reqId.isBlank()) {
                throw new MfCentralException("newCasRequest", 200, "response had no reqId");
            }

            store.add(CasRequest.awaitingQr(clientRefNo, reqId, command.detailed(), clock.instant()));
            String portalUrl = buildPortalUrl(clientRefNo, command, response, from, to);
            List<String> fields = new ArrayList<>();
            response.fieldNames().forEachRemaining(fields::add);
            log.info("CAS request started, reqId={}, detailed={}, responseFields={}", reqId, command.detailed(), fields);
            return new StartedCasRequest(clientRefNo, reqId, portalUrl, response);
        } finally {
            MDC.remove("clientRefNo");
        }
    }

    /**
     * Sends the QR image the investor downloaded from MFC's portal to validateQRCode and returns the CAS.
     * A wrong QR comes back as MFC 422 and leaves the request open for another upload.
     */
    public JsonNode fetchStatement(String clientRefNo, byte[] qrImage) {
        CasRequest request = store.find(clientRefNo).orElseThrow(() -> new CasRequestNotFoundException(clientRefNo));
        if (request.status() != CasRequest.Status.AWAITING_QR) {
            throw new CasRequestStateException(request);
        }
        if (qrImage.length == 0) {
            throw new IllegalArgumentException("QR file is empty");
        }

        MDC.put("clientRefNo", clientRefNo);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("clientRefNo", clientRefNo);
            body.put("reqId", request.reqId());
            body.put("qrCode", Base64.getEncoder().encodeToString(qrImage));

            JsonNode cas = mfCentral.call("validateQRCode", body);
            store.transition(clientRefNo, CasRequest::complete);
            log.info("CAS received, reqId={}", request.reqId());
            return cas;
        } finally {
            MDC.remove("clientRefNo");
        }
    }

    /**
     * Builds the {@code /api/auth/start?data=...} link that carries the investor's session to MFC's portal.
     * The plaintext schema and cipher were recovered from a live {@code data=} blob emitted by FundsIndia's
     * own ecas/otp backend. {@code reqId} and {@code otpRef} must be the ones MFC just returned.
     */
    private String buildPortalUrl(String clientRefNo, CasStatementCommand command, JsonNode response, LocalDate from, LocalDate to) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("clientRefNo", clientRefNo);
        data.put("reqId", response.path("reqId").asText());
        data.put("pan", command.pan());
        data.put("pekrn", "");
        data.put("email", command.email());
        data.put("mobile", localMobile(command.mobile()));
        data.put("fromDate", PORTAL_DATE.format(from));
        data.put("toDate", PORTAL_DATE.format(to));
        data.put("redirectUrl", properties.redirectUrl());
        data.put("otpRef", response.path("otpRef").asText());

        String encoded = URLEncoder.encode(portalDataCipher.encrypt(toJson(data)), UTF_8);
        // Base URL keeps its trailing slash, so the result has the same "//api/auth/start" as FundsIndia's production link.
        return properties.casPortalUrl() + "/api/auth/start?data=" + encoded;
    }

    /** MFC's portal expects the bare 10-digit number; the command normalises to +91XXXXXXXXXX. */
    private static String localMobile(String mobile) {
        return mobile.startsWith("+91") ? mobile.substring(3) : mobile;
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise portal handoff payload", e);
        }
    }

    private String newClientRefNo() {
        byte[] bytes = new byte[8];
        random.nextBytes(bytes);
        return "FIPOC" + HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT);
    }

    private static String mfcDate(LocalDate date) {
        return MFC_DATE.format(date);
    }

    /** {@code mfcResponse} is MFC's full newCasRequest reply, shown to the investor while the portal handoff is unverified. */
    public record StartedCasRequest(String clientRefNo, String reqId, String casPortalUrl, JsonNode mfcResponse) {}
}
