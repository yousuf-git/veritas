package com.veritas.provider.stripe;

import com.veritas.common.AppException;
import com.veritas.settlement.SettlementDtos;
import com.veritas.settlement.SettlementIngestService;
import com.veritas.settlement.SettlementProvider;
import com.veritas.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Present whether or not Stripe is configured, so the API surface does not change shape with
 * deployment configuration; without credentials the endpoint reports 503 rather than vanishing.
 */
@RestController
@RequestMapping("/api/v1/stripe")
@Tag(name = "Stripe sync")
public class StripeSyncController {

    private static final DateTimeFormatter FILENAME_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(java.time.ZoneOffset.UTC);

    private final Optional<StripeBalanceTransactionClient> client;
    private final SettlementIngestService ingest;

    public StripeSyncController(Optional<StripeBalanceTransactionClient> client, SettlementIngestService ingest) {
        this.client = client;
        this.ingest = ingest;
    }

    @PostMapping("/pull")
    @PreAuthorize("hasAuthority('" + Role.Permissions.FILE_UPLOAD + "')")
    @Operation(summary = "Pull real balance transactions from Stripe and ingest them as a settlement file")
    public ResponseEntity<SettlementDtos.FileResponse> pull(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        StripeBalanceTransactionClient stripe = client.orElseThrow(StripeNotConfiguredException::new);

        byte[] report = stripe.fetchReport(from, to);
        String filename = "stripe-balance-%s-to-%s.csv".formatted(FILENAME_STAMP.format(from),
                FILENAME_STAMP.format(to));

        SettlementIngestService.Ingested result = ingest.ingest(filename, SettlementProvider.STRIPE, report);
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(SettlementDtos.FileResponse.from(result.file(), result.created()));
    }

    static class StripeNotConfiguredException extends AppException {
        StripeNotConfiguredException() {
            super(HttpStatus.SERVICE_UNAVAILABLE, "STRIPE_NOT_CONFIGURED",
                    "Stripe access is not configured. Set app.stripe.enabled=true and app.stripe.api-key, "
                            + "or upload a settlement file instead.");
        }
    }
}
