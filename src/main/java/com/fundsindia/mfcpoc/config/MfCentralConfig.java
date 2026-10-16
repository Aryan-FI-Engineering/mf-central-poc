package com.fundsindia.mfcpoc.config;

import com.fundsindia.mfcpoc.crypto.PayloadCipher;
import com.fundsindia.mfcpoc.crypto.PayloadSigner;
import com.fundsindia.mfcpoc.crypto.PortalDataCipher;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class MfCentralConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RestClient mfCentralRestClient(RestClient.Builder builder, MfCentralProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        // CAS assembly fans out to both RTAs on MFC's side; give it room.
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        return builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    }

    @Bean
    PayloadCipher payloadCipher(MfCentralProperties properties) {
        return new PayloadCipher(properties.encryptionKey(), properties.iv());
    }

    @Bean
    PortalDataCipher portalDataCipher(MfCentralProperties properties) {
        return new PortalDataCipher(properties.qrUrlEncryptionKey(), properties.iv());
    }

    @Bean
    PayloadSigner payloadSigner(MfCentralProperties properties) {
        return new PayloadSigner(read(properties.privateKeyFile()), read(properties.publicKeyFile()));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read key file " + path, e);
        }
    }
}
