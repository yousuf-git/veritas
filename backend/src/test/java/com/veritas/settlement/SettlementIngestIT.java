package com.veritas.settlement;

import com.jayway.jsonpath.JsonPath;
import com.veritas.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SettlementIngestIT extends AbstractIntegrationTest {

    private static final String REPORT = """
            balance_transaction_id,created_utc,available_on_utc,currency,gross,fee,net,reporting_category,source_id,charge_id,description
            txn_a1,2026-02-01 09:00:00,2026-02-03 09:00:00,usd,100.00,3.20,96.80,charge,ch_a1,ch_a1,Payment for ORD-A1
            txn_a2,2026-02-01 10:00:00,2026-02-03 10:00:00,usd,250.00,7.55,242.45,charge,ch_a2,ch_a2,Payment for ORD-A2
            """;

    @Test
    @DisplayName("uploading identical bytes twice ingests once and reports the second as a duplicate")
    void reUploadIsANoOp() throws Exception {
        String analyst = tokenFor("analyst");

        MockMultipartFile file = new MockMultipartFile(
                "file", "payout.csv", "text/csv", REPORT.getBytes(StandardCharsets.UTF_8));

        String first = mvc.perform(multipart("/api/v1/settlement-files").file(file)
                        .param("provider", "STRIPE")
                        .header("Authorization", analyst))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat((boolean) JsonPath.read(first, "$.newlyIngested")).isTrue();
        assertThat((int) JsonPath.read(first, "$.lineCount")).isEqualTo(2);
        String fileId = JsonPath.read(first, "$.id");

        MockMultipartFile again = new MockMultipartFile(
                "file", "renamed-but-identical.csv", "text/csv", REPORT.getBytes(StandardCharsets.UTF_8));

        String second = mvc.perform(multipart("/api/v1/settlement-files").file(again)
                        .param("provider", "STRIPE")
                        .header("Authorization", analyst))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Same content hash, so it resolves to the original file rather than creating a second one.
        assertThat((String) JsonPath.read(second, "$.id")).isEqualTo(fileId);
        assertThat((boolean) JsonPath.read(second, "$.newlyIngested")).isFalse();

        mvc.perform(get("/api/v1/settlement-files/{id}/lines", fileId).header("Authorization", analyst))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("a file that is not a balance report is rejected with a readable reason")
    void rejectsUnparseableFile() throws Exception {
        String analyst = tokenFor("analyst");

        MockMultipartFile junk = new MockMultipartFile(
                "file", "notes.csv", "text/csv", "hello,world\n1,2\n".getBytes(StandardCharsets.UTF_8));

        String response = mvc.perform(multipart("/api/v1/settlement-files").file(junk)
                        .param("provider", "STRIPE")
                        .header("Authorization", analyst))
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(response, "$.code")).isEqualTo("SETTLEMENT_PARSE_ERROR");
    }
}
