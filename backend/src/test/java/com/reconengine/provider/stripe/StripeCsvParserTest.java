package com.reconengine.provider.stripe;

import com.reconengine.settlement.ParsedSettlementLine;
import com.reconengine.settlement.SettlementParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripeCsvParserTest {

    private static final String HEADER =
            "balance_transaction_id,created_utc,available_on_utc,currency,gross,fee,net,"
                    + "reporting_category,source_id,charge_id,description\n";

    private final StripeCsvParser parser = new StripeCsvParser();

    private List<ParsedSettlementLine> parse(String csv) {
        return parser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("converts the report's major units into exact minor units")
    void convertsMajorUnitsToMinorUnits() {
        List<ParsedSettlementLine> lines = parse(HEADER
                + "txn_1,2026-01-05 10:22:31,2026-01-07 10:22:31,usd,120.00,3.78,116.22,charge,ch_1,ch_1,Order ORD-1\n");

        assertThat(lines).hasSize(1);
        ParsedSettlementLine line = lines.getFirst();
        assertThat(line.grossMinor()).isEqualTo(12_000L);
        assertThat(line.feeMinor()).isEqualTo(378L);
        assertThat(line.netMinor()).isEqualTo(11_622L);
        assertThat(line.currency()).isEqualTo("USD");
        assertThat(line.providerRef()).isEqualTo("ch_1");
        assertThat(line.createdAt()).isEqualTo(Instant.parse("2026-01-05T10:22:31Z"));
    }

    @Test
    @DisplayName("keeps a zero-decimal currency whole instead of multiplying it")
    void handlesZeroDecimalCurrency() {
        List<ParsedSettlementLine> lines = parse(HEADER
                + "txn_1,2026-01-05 10:22:31,,jpy,1200,36,1164,charge,ch_1,ch_1,Order ORD-1\n");

        assertThat(lines.getFirst().grossMinor()).isEqualTo(1_200L);
        assertThat(lines.getFirst().availableOn()).isNull();
    }

    @Test
    @DisplayName("refuses to round away sub-cent precision")
    void refusesSubCentPrecision() {
        assertThatThrownBy(() -> parse(HEADER
                + "txn_1,2026-01-05 10:22:31,,usd,120.005,0.00,120.005,charge,ch_1,ch_1,x\n"))
                .isInstanceOf(SettlementParseException.class)
                .hasMessageContaining("more precision than USD");
    }

    @Test
    @DisplayName("rejects a row where net does not equal gross minus fee")
    void rejectsBrokenNetIdentity() {
        assertThatThrownBy(() -> parse(HEADER
                + "txn_1,2026-01-05 10:22:31,,usd,120.00,3.78,999.00,charge,ch_1,ch_1,x\n"))
                .isInstanceOf(SettlementParseException.class)
                .hasMessageContaining("net");
    }

    @Test
    @DisplayName("accepts ISO-8601 timestamps as well as the report's space-separated form")
    void acceptsBothTimestampFormats() {
        List<ParsedSettlementLine> lines = parse(HEADER
                + "txn_1,2026-01-05T10:22:31Z,,usd,10.00,0.00,10.00,charge,ch_1,ch_1,x\n");

        assertThat(lines.getFirst().createdAt()).isEqualTo(Instant.parse("2026-01-05T10:22:31Z"));
    }

    @Test
    @DisplayName("names the columns it needs when the file is not a balance report")
    void reportsMissingColumns() {
        assertThatThrownBy(() -> parse("id,amount\ntxn_1,100\n"))
                .isInstanceOf(SettlementParseException.class)
                .hasMessageContaining("Missing required Stripe report columns");
    }

    @Test
    @DisplayName("tolerates extra and reordered columns, since reports are configurable")
    void toleratesExtraAndReorderedColumns() {
        String csv = "created_utc,currency,net,gross,fee,balance_transaction_id,reporting_category,"
                + "source_id,customer_email\n"
                + "2026-01-05 10:22:31,usd,9.41,10.00,0.59,txn_9,charge,ch_9,a@example.com\n";

        ParsedSettlementLine line = parse(csv).getFirst();
        assertThat(line.providerTxnId()).isEqualTo("txn_9");
        assertThat(line.netMinor()).isEqualTo(941L);
        // The raw snapshot keeps the columns worth auditing and drops the rest.
        assertThat(line.raw()).containsEntry("source_id", "ch_9").doesNotContainKey("customer_email");
    }

    @Test
    @DisplayName("a header with no rows is a parse failure, not an empty success")
    void rejectsHeaderOnlyFile() {
        assertThatThrownBy(() -> parse(HEADER))
                .isInstanceOf(SettlementParseException.class)
                .hasMessageContaining("no transaction rows");
    }
}
