package com.fundsindia.mfcpoc.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Onboarding details shared by MFCentral. Startup fails fast if any value is missing.
 * {@link #toString()} is overridden so secrets never reach a log line.
 */
@Validated
@ConfigurationProperties("mfc")
public record MfCentralProperties(
        @NotBlank String baseUrl,
        @NotBlank String tokenPath,
        @NotBlank String casPortalUrl,
        @NotBlank String redirectUrl,
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String encryptionKey,
        @NotBlank String qrUrlEncryptionKey,
        @NotBlank String iv,
        @NotNull Path privateKeyFile,
        @NotNull Path publicKeyFile) {

    @Override
    public String toString() {
        return "MfCentralProperties[baseUrl=%s, casPortalUrl=%s, redirectUrl=%s, clientId=%s, secrets=****]"
                .formatted(baseUrl, casPortalUrl, redirectUrl, clientId);
    }
}
