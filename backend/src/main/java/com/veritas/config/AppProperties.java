package com.veritas.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/** All tunable behaviour of the engine, validated at boot so a bad deployment fails immediately. */
@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(
        @NotNull @Valid Jwt jwt,
        @NotNull @Valid Storage storage,
        @NotNull @Valid Matching matching,
        @NotNull @Valid Stripe stripe,
        @NotNull @Valid Cors cors,
        @NotNull @Valid Seed seed) {

    public record Jwt(
            /* HS256 needs at least 256 bits of key material. */
            @NotBlank @Size(min = 32, message = "jwt secret must be at least 32 characters") String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl) {
    }

    public record Storage(@NotBlank String root) {
    }

    public record Matching(
            /* How far apart a ledger entry and a settlement line may be and still be considered the same event. */
            @NotNull Duration dateWindow,
            /* Cent-level drift treated as FX rounding rather than a real amount difference. */
            @Min(0) long amountToleranceMinor,
            @DecimalMin("0.0") @DecimalMax("1.0") double minHeuristicConfidence) {
    }

    public record Stripe(
            boolean enabled,
            String apiKey,
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout) {
    }

    public record Cors(@NotNull List<String> allowedOrigins) {
    }

    /** Creates the demo finance users on an empty database. Turn off for any real deployment. */
    public record Seed(boolean users, @NotBlank @Size(min = 8) String defaultPassword) {
    }
}
