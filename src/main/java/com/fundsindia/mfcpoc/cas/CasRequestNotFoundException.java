package com.fundsindia.mfcpoc.cas;

public class CasRequestNotFoundException extends RuntimeException {

    public CasRequestNotFoundException(String clientRefNo) {
        super("No CAS request with clientRefNo " + clientRefNo);
    }
}
