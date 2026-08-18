package com.veritas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Injected rather than called statically so time-dependent matching stays testable. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
