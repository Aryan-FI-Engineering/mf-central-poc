package com.fundsindia.mfcpoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MfCentralPocApplication {

    public static void main(String[] args) {
        SpringApplication.run(MfCentralPocApplication.class, args);
    }
}
