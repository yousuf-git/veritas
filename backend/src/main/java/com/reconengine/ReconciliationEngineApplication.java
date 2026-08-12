package com.reconengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ReconciliationEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconciliationEngineApplication.class, args);
    }
}
