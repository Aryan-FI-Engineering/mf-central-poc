package com.fundsindia.mfcpoc.cas;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

/** In-memory only: a restart loses in-flight journeys. Acceptable for a single-user POC. */
@Component
public class CasRequestStore {

    private final Map<String, CasRequest> requests = new ConcurrentHashMap<>();

    void add(CasRequest request) {
        if (requests.putIfAbsent(request.clientRefNo(), request) != null) {
            throw new IllegalStateException("Duplicate clientRefNo " + request.clientRefNo());
        }
    }

    Optional<CasRequest> find(String clientRefNo) {
        return Optional.ofNullable(requests.get(clientRefNo));
    }

    /** Applies a state transition atomically; the transition throws if it is illegal from the current state. */
    CasRequest transition(String clientRefNo, UnaryOperator<CasRequest> transition) {
        CasRequest updated = requests.computeIfPresent(clientRefNo, (key, current) -> transition.apply(current));
        if (updated == null) {
            throw new CasRequestNotFoundException(clientRefNo);
        }
        return updated;
    }

    public List<CasRequest> withStatus(CasRequest.Status status) {
        return requests.values().stream()
                .filter(r -> r.status() == status)
                .sorted(Comparator.comparing(CasRequest::createdAt).reversed())
                .toList();
    }
}
