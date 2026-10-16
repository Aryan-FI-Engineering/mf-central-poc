package com.fundsindia.mfcpoc.web;

import com.fundsindia.mfcpoc.cas.CasRequestNotFoundException;
import com.fundsindia.mfcpoc.cas.CasRequestStateException;
import com.fundsindia.mfcpoc.client.MfCentralException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** MFC's own business errors (400/422) are passed through so the user can act on them; the rest become 502. */
    @ExceptionHandler
    ProblemDetail mfCentral(MfCentralException e) {
        log.warn(e.getMessage());
        HttpStatus status = e.httpStatus() == 400 || e.httpStatus() == 422
                ? HttpStatus.valueOf(e.httpStatus())
                : HttpStatus.BAD_GATEWAY;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setProperty("mfcStatus", e.httpStatus());
        problem.setProperty("mfcErrors", e.errors());
        return problem;
    }

    @ExceptionHandler
    ProblemDetail unreachable(ResourceAccessException e) {
        log.warn("MFCentral unreachable: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT,
                "MFCentral did not respond. Do not resubmit blindly: the request may have been processed.");
    }

    /** Raised by the token call, which uses retrieve(). The body is not echoed: it may contain credential hints. */
    @ExceptionHandler
    ProblemDetail upstreamHttp(RestClientResponseException e) {
        log.warn("MFCentral HTTP {} on token request", e.getStatusCode().value());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "MFCentral token request failed with HTTP " + e.getStatusCode().value());
        problem.setProperty("mfcStatus", e.getStatusCode().value());
        return problem;
    }

    @ExceptionHandler
    ProblemDetail badBody(HttpMessageNotReadableException e) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        String detail = cause instanceof IllegalArgumentException ? cause.getMessage() : "Malformed request body";
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler
    ProblemDetail badInput(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail notFound(CasRequestNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail conflict(CasRequestStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
