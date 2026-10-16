package com.fundsindia.mfcpoc.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

/**
 * MFCentral rejected a call or answered with something we cannot trust. Carries only MFC's
 * {@code errors} array, never the request or response payload, so investor PII stays out of logs.
 */
public class MfCentralException extends RuntimeException {

    private final String endpoint;
    private final int httpStatus;
    private final JsonNode errors;

    public MfCentralException(String endpoint, int httpStatus, JsonNode errors) {
        super("MFCentral %s returned HTTP %d: %s".formatted(endpoint, httpStatus, errors));
        this.endpoint = endpoint;
        this.httpStatus = httpStatus;
        this.errors = errors;
    }

    public MfCentralException(String endpoint, int httpStatus, String reason) {
        super("MFCentral %s (HTTP %d): %s".formatted(endpoint, httpStatus, reason));
        this.endpoint = endpoint;
        this.httpStatus = httpStatus;
        this.errors = MissingNode.getInstance();
    }

    /** {@code errors} goes to the caller only; the message stays generic so it is safe to log. */
    public MfCentralException(String endpoint, int httpStatus, String reason, JsonNode errors) {
        super("MFCentral %s (HTTP %d): %s".formatted(endpoint, httpStatus, reason));
        this.endpoint = endpoint;
        this.httpStatus = httpStatus;
        this.errors = errors;
    }

    public String endpoint() {
        return endpoint;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public JsonNode errors() {
        return errors;
    }
}
