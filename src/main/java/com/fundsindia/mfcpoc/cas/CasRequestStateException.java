package com.fundsindia.mfcpoc.cas;

/** An operation is illegal from the CAS request's current state. */
public class CasRequestStateException extends IllegalStateException {

    public CasRequestStateException(CasRequest request) {
        super("CAS request " + request.clientRefNo() + " is already " + request.status());
    }
}
