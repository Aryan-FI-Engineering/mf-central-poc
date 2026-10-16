package com.fundsindia.mfcpoc.cas;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An investor's request for a CAS. A date range asks MFC for the detailed CAS (transactions);
 * no range asks for the summary. Normalised and validated on construction, so a bad instance cannot exist.
 */
public record CasStatementCommand(String pan, String mobile, String email, LocalDate fromDate, LocalDate toDate) {

    private static final Pattern PAN = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]");
    private static final Pattern INDIAN_MOBILE = Pattern.compile("\\+91[6-9][0-9]{9}");

    public CasStatementCommand {
        pan = trim(pan).toUpperCase(Locale.ROOT);
        if (!PAN.matcher(pan).matches()) {
            throw new IllegalArgumentException("PAN must look like ABCDE1234F");
        }

        mobile = normaliseMobile(trim(mobile));
        email = trim(email);
        if (mobile.isEmpty() && email.isEmpty()) {
            throw new IllegalArgumentException("Either mobile or email registered against the PAN is required");
        }
        if (!email.isEmpty() && !email.contains("@")) {
            throw new IllegalArgumentException("Email is not valid");
        }

        if ((fromDate == null) != (toDate == null)) {
            throw new IllegalArgumentException("Give both fromDate and toDate for a detailed CAS, or neither for a summary");
        }
        if (fromDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }
    }

    public boolean detailed() {
        return fromDate != null;
    }

    private static String normaliseMobile(String raw) {
        if (raw.isEmpty()) {
            return raw;
        }
        String digits = raw.replaceAll("[\\s-]", "");
        String withCountryCode = digits.length() == 10 ? "+91" + digits : digits;
        if (!INDIAN_MOBILE.matcher(withCountryCode).matches()) {
            throw new IllegalArgumentException("Mobile must be a 10-digit Indian number, optionally prefixed with +91");
        }
        return withCountryCode;
    }

    private static String trim(String value) {
        return value == null ? "" : value.strip();
    }
}
