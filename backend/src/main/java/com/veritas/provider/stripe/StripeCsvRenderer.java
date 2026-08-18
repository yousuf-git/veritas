package com.veritas.provider.stripe;

import com.veritas.common.Money;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes rows in Stripe's own itemized balance report format. Both the demo generator and the
 * live API puller render through here, so a file pulled from Stripe and a file produced locally
 * go through exactly the same parser on the way back in.
 */
@Component
public class StripeCsvRenderer {

    static final String[] HEADERS = {
            "balance_transaction_id", "created_utc", "available_on_utc", "currency",
            "gross", "fee", "net", "reporting_category", "source_id", "charge_id", "description"
    };

    private static final DateTimeFormatter REPORT_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    public byte[] render(List<StripeReportRow> rows) {
        StringWriter out = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(HEADERS).get();

        try (CSVPrinter printer = new CSVPrinter(out, format)) {
            for (StripeReportRow row : rows) {
                String currency = row.currency().toLowerCase();
                printer.printRecord(
                        row.balanceTransactionId(),
                        REPORT_TIMESTAMP.format(row.createdAt()),
                        timestampOrEmpty(row.availableOn()),
                        currency,
                        major(row.grossMinor(), row.currency()),
                        major(row.feeMinor(), row.currency()),
                        major(row.netMinor(), row.currency()),
                        row.reportingCategory(),
                        nullToEmpty(row.sourceId()),
                        nullToEmpty(row.chargeId()),
                        nullToEmpty(row.description()));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("failed to render settlement report", ex);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String timestampOrEmpty(Instant value) {
        return value == null ? "" : REPORT_TIMESTAMP.format(value);
    }

    private String major(long minorUnits, String currency) {
        return BigDecimal.valueOf(minorUnits, Money.fractionDigits(currency)).toPlainString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
