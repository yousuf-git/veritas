package com.veritas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VeritasApplication {

    public static void main(String[] args) {
        SpringApplication.run(VeritasApplication.class, args);
    }
}
