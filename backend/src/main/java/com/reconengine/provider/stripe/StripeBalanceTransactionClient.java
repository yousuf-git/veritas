package com.reconengine.provider.stripe;

import com.reconengine.common.Errors;
import com.reconengine.config.AppProperties;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.net.RequestOptions;
import com.stripe.param.BalanceTransactionListParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Pulls real balance transactions from Stripe (test mode is fine) and renders them into the
 * same report format the CSV upload path consumes. Going back out through the file format is
 * deliberate: the pulled data is stored verbatim, checksummed and idempotent exactly like an
 * uploaded file, and only one parser has to be trusted.
 * <p>
 * Only created when {@code app.stripe.enabled} is true, so the service runs with no Stripe
 * credentials at all.
 */
@Component
@ConditionalOnProperty(prefix = "app.stripe", name = "enabled", havingValue = "true")
public class StripeBalanceTransactionClient {

    private static final Logger log = LoggerFactory.getLogger(StripeBalanceTransactionClient.class);

    /** Stripe's maximum page size; auto-pagination walks the rest. */
    private static final long PAGE_SIZE = 100L;

    /** Refuses to walk an unbounded history if someone asks for an enormous window. */
    private static final int MAX_TRANSACTIONS = 25_000;

    private final StripeClient stripe;
    private final StripeCsvRenderer renderer;
    private final RequestOptions requestOptions;

    public StripeBalanceTransactionClient(AppProperties properties, StripeCsvRenderer renderer) {
        AppProperties.Stripe config = properties.stripe();
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "app.stripe.enabled is true but app.stripe.api-key is not set");
        }

        this.stripe = new StripeClient(config.apiKey());
        this.renderer = renderer;
        this.requestOptions = RequestOptions.builder()
                .setConnectTimeout((int) config.connectTimeout().toMillis())
                .setReadTimeout((int) config.readTimeout().toMillis())
                .build();
    }

    /** Fetches the window and returns it as report bytes, ready for the normal ingest path. */
    public byte[] fetchReport(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new Errors.BadRequest("INVALID_WINDOW", "The 'from' instant must be before 'to'.");
        }

        BalanceTransactionListParams params = BalanceTransactionListParams.builder()
                .setCreated(BalanceTransactionListParams.Created.builder()
                        .setGte(from.getEpochSecond())
                        .setLte(to.getEpochSecond())
                        .build())
                .setLimit(PAGE_SIZE)
                .build();

        List<StripeReportRow> rows = new ArrayList<>();
        try {
            for (BalanceTransaction transaction : stripe.balanceTransactions()
                    .list(params, requestOptions).autoPagingIterable()) {

                rows.add(toRow(transaction));
                if (rows.size() >= MAX_TRANSACTIONS) {
                    throw new Errors.Unprocessable("WINDOW_TOO_LARGE",
                            "The requested window contains more than %d balance transactions; narrow it."
                                    .formatted(MAX_TRANSACTIONS));
                }
            }
        } catch (StripeException ex) {
            log.error("Stripe balance transaction fetch failed", ex);
            throw new Errors.Unprocessable("STRIPE_FETCH_FAILED",
                    "Could not fetch balance transactions from Stripe: " + ex.getMessage());
        }

        if (rows.isEmpty()) {
            throw new Errors.Unprocessable("NO_TRANSACTIONS",
                    "Stripe returned no balance transactions between " + from + " and " + to + ".");
        }

        log.info("fetched {} Stripe balance transactions between {} and {}", rows.size(), from, to);
        return renderer.render(rows);
    }

    private StripeReportRow toRow(BalanceTransaction transaction) {
        // The API already speaks minor units, unlike the CSV reports, so no conversion is needed.
        return new StripeReportRow(
                transaction.getId(),
                Instant.ofEpochSecond(transaction.getCreated()),
                transaction.getAvailableOn() == null ? null : Instant.ofEpochSecond(transaction.getAvailableOn()),
                transaction.getCurrency().toUpperCase(),
                transaction.getAmount(),
                transaction.getFee() == null ? 0L : transaction.getFee(),
                transaction.getReportingCategory() == null ? "charge" : transaction.getReportingCategory(),
                transaction.getSource(),
                transaction.getSource(),
                transaction.getDescription());
    }
}
