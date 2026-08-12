package com.reconengine.provider.stripe;

import com.reconengine.common.Money;
import com.reconengine.settlement.ParsedSettlementLine;
import com.reconengine.settlement.SettlementParseException;
import com.reconengine.settlement.SettlementParser;
import com.reconengine.settlement.SettlementProvider;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses Stripe's itemized balance report (the {@code balance_change_from_activity.itemized}
 * family). Columns are matched by header name, so extra or reordered columns are tolerated —
 * Stripe lets you choose which columns a report includes.
 * <p>
 * Amounts in these reports are in <em>major</em> units ("120.00"), while everything downstream
 * is in minor units, so conversion happens here and refuses to round: a value with more
 * precision than the currency allows is a parse failure, never a silently dropped fraction.
 */
@Component
public class StripeCsvParser implements SettlementParser {

    private static final String COL_TXN_ID = "balance_transaction_id";
    private static final String COL_CREATED = "created_utc";
    private static final String COL_AVAILABLE_ON = "available_on_utc";
    private static final String COL_CURRENCY = "currency";
    private static final String COL_GROSS = "gross";
    private static final String COL_FEE = "fee";
    private static final String COL_NET = "net";
    private static final String COL_CATEGORY = "reporting_category";
    private static final String COL_SOURCE_ID = "source_id";
    private static final String COL_CHARGE_ID = "charge_id";
    private static final String COL_DESCRIPTION = "description";

    private static final Set<String> REQUIRED_COLUMNS = Set.of(
            COL_TXN_ID, COL_CREATED, COL_CURRENCY, COL_GROSS, COL_FEE, COL_NET, COL_CATEGORY);

    /** Columns worth keeping verbatim for audit drill-down; the rest of the row is dropped. */
    private static final List<String> RETAINED_COLUMNS = List.of(
            COL_TXN_ID, COL_SOURCE_ID, COL_CHARGE_ID, COL_CATEGORY, COL_CURRENCY,
            COL_GROSS, COL_FEE, COL_NET, COL_CREATED, COL_AVAILABLE_ON, COL_DESCRIPTION,
            "customer_facing_amount", "customer_facing_currency", "payment_intent_id",
            "automatic_payout_id");

    private static final DateTimeFormatter SPACE_SEPARATED =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public SettlementProvider provider() {
        return SettlementProvider.STRIPE;
    }

    @Override
    public List<ParsedSettlementLine> parse(InputStream input) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .get();

        try (CSVParser parser = CSVParser.parse(
                new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)), format)) {

            requireColumns(parser.getHeaderMap().keySet());

            List<ParsedSettlementLine> lines = new ArrayList<>();
            int lineNumber = 0;
            for (CSVRecord record : parser) {
                lineNumber++;
                lines.add(toLine(record, lineNumber));
            }
            if (lines.isEmpty()) {
                throw new SettlementParseException("The file contains a header but no transaction rows.");
            }
            return lines;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void requireColumns(Set<String> headers) {
        List<String> missing = REQUIRED_COLUMNS.stream()
                .filter(required -> headers.stream().noneMatch(h -> h.equalsIgnoreCase(required)))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw new SettlementParseException("Missing required Stripe report columns: " + String.join(", ", missing));
        }
    }

    private ParsedSettlementLine toLine(CSVRecord record, int lineNumber) {
        String currency = value(record, COL_CURRENCY).toUpperCase();
        if (!currency.matches("^[A-Z]{3}$")) {
            throw SettlementParseException.atLine(lineNumber, "'" + currency + "' is not an ISO-4217 currency code");
        }

        long gross = toMinorUnits(value(record, COL_GROSS), currency, lineNumber, COL_GROSS);
        long fee = toMinorUnits(valueOrDefault(record, COL_FEE, "0"), currency, lineNumber, COL_FEE);
        long net = toMinorUnits(value(record, COL_NET), currency, lineNumber, COL_NET);

        if (net != gross - fee) {
            throw SettlementParseException.atLine(lineNumber,
                    "net (%d) does not equal gross (%d) minus fee (%d)".formatted(net, gross, fee));
        }

        String txnId = value(record, COL_TXN_ID);
        if (txnId.isBlank()) {
            throw SettlementParseException.atLine(lineNumber, "balance_transaction_id is empty");
        }

        String sourceId = valueOrDefault(record, COL_SOURCE_ID, "");
        if (sourceId.isBlank()) {
            sourceId = valueOrDefault(record, COL_CHARGE_ID, "");
        }

        return new ParsedSettlementLine(
                lineNumber,
                txnId,
                sourceId.isBlank() ? null : sourceId,
                value(record, COL_CATEGORY),
                gross,
                fee,
                net,
                currency,
                parseTimestamp(value(record, COL_CREATED), lineNumber, COL_CREATED),
                parseOptionalTimestamp(valueOrDefault(record, COL_AVAILABLE_ON, ""), lineNumber),
                emptyToNull(valueOrDefault(record, COL_DESCRIPTION, "")),
                retainedColumns(record));
    }

    private Map<String, String> retainedColumns(CSVRecord record) {
        Map<String, String> raw = new LinkedHashMap<>();
        for (String column : RETAINED_COLUMNS) {
            if (record.isMapped(column)) {
                String value = record.get(column);
                if (value != null && !value.isBlank()) {
                    raw.put(column, value);
                }
            }
        }
        return raw;
    }

    static long toMinorUnits(String raw, String currency, int lineNumber, String column) {
        String cleaned = raw.trim().replace(",", "");
        if (cleaned.isEmpty()) {
            return 0L;
        }
        try {
            return new BigDecimal(cleaned)
                    .movePointRight(Money.fractionDigits(currency))
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (ArithmeticException ex) {
            throw SettlementParseException.atLine(lineNumber,
                    "%s '%s' has more precision than %s supports".formatted(column, raw, currency));
        } catch (NumberFormatException ex) {
            throw SettlementParseException.atLine(lineNumber, "%s '%s' is not a number".formatted(column, raw));
        }
    }

    private Instant parseOptionalTimestamp(String raw, int lineNumber) {
        return raw.isBlank() ? null : parseTimestamp(raw, lineNumber, COL_AVAILABLE_ON);
    }

    /** Stripe reports use "yyyy-MM-dd HH:mm:ss" in UTC; API-derived files use ISO-8601. Accept both. */
    static Instant parseTimestamp(String raw, int lineNumber, String column) {
        String value = raw.trim();
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // fall through to the report formats
        }
        try {
            return LocalDateTime.parse(value, SPACE_SEPARATED).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // fall through to the date-only format
        }
        try {
            return LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            throw SettlementParseException.atLine(lineNumber,
                    "%s '%s' is not a recognised UTC timestamp".formatted(column, raw));
        }
    }

    private String value(CSVRecord record, String column) {
        if (!record.isMapped(column)) {
            throw new SettlementParseException("Missing required Stripe report column: " + column);
        }
        String value = record.get(column);
        return value == null ? "" : value.trim();
    }

    private String valueOrDefault(CSVRecord record, String column, String fallback) {
        if (!record.isMapped(column)) {
            return fallback;
        }
        String value = record.get(column);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String emptyToNull(String value) {
        return value.isBlank() ? null : value;
    }
}
