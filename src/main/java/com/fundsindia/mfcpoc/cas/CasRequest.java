package com.fundsindia.mfcpoc.cas;

import java.time.Instant;
import java.util.Objects;

/**
 * One CAS journey, keyed by our {@code clientRefNo}.
 *
 * <pre>
 * AWAITING_QR --(validateQRCode succeeded)--> COMPLETED (terminal)
 * </pre>
 * A rejected QR (MFC 422) leaves the request in AWAITING_QR so the investor can upload again.
 */
public record CasRequest(String clientRefNo, String reqId, boolean detailed, Status status, Instant createdAt) {

    public enum Status { AWAITING_QR, COMPLETED }

    public CasRequest {
        Objects.requireNonNull(clientRefNo, "clientRefNo");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (reqId == null || reqId.isBlank()) {
            throw new IllegalArgumentException("reqId from newCasRequest is required");
        }
    }

    static CasRequest awaitingQr(String clientRefNo, String reqId, boolean detailed, Instant createdAt) {
        return new CasRequest(clientRefNo, reqId, detailed, Status.AWAITING_QR, createdAt);
    }

    CasRequest complete() {
        if (status != Status.AWAITING_QR) {
            throw new CasRequestStateException(this);
        }
        return new CasRequest(clientRefNo, reqId, detailed, Status.COMPLETED, createdAt);
    }
}
