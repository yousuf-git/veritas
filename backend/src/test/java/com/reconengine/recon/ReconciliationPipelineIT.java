package com.reconengine.recon;

import com.jayway.jsonpath.JsonPath;
import com.reconengine.AbstractIntegrationTest;
import net.minidev.json.JSONArray;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The end-to-end proof: a scenario with a known set of injected defects must produce exactly
 * that set of classified discrepancies, and everything else must match automatically.
 */
class ReconciliationPipelineIT extends AbstractIntegrationTest {

    private static final Map<String, Integer> EXPECTED_DEFECTS = Map.of(
            "MISSING_PAYOUT", 3,
            "MISSING_LEDGER_ENTRY", 2,
            "AMOUNT_DRIFT", 2,
            "FX_ROUNDING", 3,
            "DUPLICATE_CHARGE", 2,
            "UNEXPECTED_FEE", 2);

    private static final String SCENARIO = """
            {
              "transactions": 120,
              "seed": 20260812,
              "currency": "USD",
              "missingPayouts": 3,
              "missingLedgerEntries": 2,
              "amountDrifts": 2,
              "fxRoundings": 3,
              "duplicateCharges": 2,
              "unexpectedFees": 2,
              "heuristicOnly": 10
            }
            """;

    @Test
    @DisplayName("classifies every injected defect and matches the rest automatically")
    void reconcilesAScenarioEndToEnd() throws Exception {
        String admin = tokenFor("admin");

        String scenario = mvc.perform(post("/api/v1/demo/scenario")
                        .header("Authorization", admin)
                        .contentType("application/json")
                        .content(SCENARIO))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String fileId = JsonPath.read(scenario, "$.file.id");
        assertThat((int) JsonPath.read(scenario, "$.ledgerEntriesCreated")).isPositive();

        String triggered = mvc.perform(post("/api/v1/runs")
                        .header("Authorization", admin)
                        .param("fileId", fileId))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String runId = JsonPath.read(triggered, "$.id");

        String report = awaitUntil(Duration.ofSeconds(90),
                () -> readReport(admin, runId),
                body -> !"PENDING".equals(JsonPath.read(body, "$.status"))
                        && !"RUNNING".equals(JsonPath.read(body, "$.status")));

        assertThat((String) JsonPath.read(report, "$.status")).isEqualTo("COMPLETED");

        Map<String, Integer> observed = countsByType(report);
        assertThat(observed).containsExactlyInAnyOrderEntriesOf(EXPECTED_DEFECTS);

        int totalDiscrepancies = (int) JsonPath.read(report, "$.discrepancyCount");
        assertThat(totalDiscrepancies).isEqualTo(EXPECTED_DEFECTS.values().stream().mapToInt(Integer::intValue).sum());

        // Everything that is not a deliberate defect must have matched, via one stage or the other.
        double matchRate = ((Number) JsonPath.read(report, "$.matchRate")).doubleValue();
        assertThat(matchRate).isGreaterThan(0.94);

        assertThat((int) JsonPath.read(report, "$.matchedHeuristic"))
                .as("rows delivered without a provider reference must be matched by the heuristic stage")
                .isGreaterThanOrEqualTo(10);

        assertThat((int) JsonPath.read(report, "$.matchedExact")).isPositive();
    }

    @Test
    @DisplayName("re-running a file that already has a completed run produces a second, independent run")
    void allowsASecondRunAfterTheFirstCompletes() throws Exception {
        String admin = tokenFor("admin");

        String scenario = mvc.perform(post("/api/v1/demo/scenario")
                        .header("Authorization", admin)
                        .contentType("application/json")
                        .content("""
                                {"transactions": 20, "seed": 777, "currency": "EUR",
                                 "missingPayouts": 1, "missingLedgerEntries": 0, "amountDrifts": 1,
                                 "fxRoundings": 0, "duplicateCharges": 0, "unexpectedFees": 0,
                                 "heuristicOnly": 0}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String fileId = JsonPath.read(scenario, "$.file.id");

        String firstRunId = triggerAndAwait(admin, fileId);
        String secondRunId = triggerAndAwait(admin, fileId);

        assertThat(firstRunId).isNotEqualTo(secondRunId);

        // The second run re-derives the same verdicts rather than inheriting the first run's.
        String secondReport = readReport(admin, secondRunId);
        assertThat((int) JsonPath.read(secondReport, "$.discrepancyCount")).isEqualTo(2);
    }

    private String triggerAndAwait(String token, String fileId) throws Exception {
        String triggered = mvc.perform(post("/api/v1/runs")
                        .header("Authorization", token)
                        .param("fileId", fileId))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String runId = JsonPath.read(triggered, "$.id");
        awaitUntil(Duration.ofSeconds(90),
                () -> readReport(token, runId),
                body -> "COMPLETED".equals(JsonPath.read(body, "$.status"))
                        || "FAILED".equals(JsonPath.read(body, "$.status")));
        return runId;
    }

    private String readReport(String token, String runId) {
        try {
            return mvc.perform(get("/api/v1/runs/{id}/report", runId).header("Authorization", token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to read run report", ex);
        }
    }

    private Map<String, Integer> countsByType(String report) {
        JSONArray types = JsonPath.read(report, "$.discrepancies[*].type");
        JSONArray counts = JsonPath.read(report, "$.discrepancies[*].count");

        Map<String, Integer> observed = new java.util.HashMap<>();
        for (int i = 0; i < types.size(); i++) {
            // A type can appear once per severity, so the counts are summed.
            observed.merge(String.valueOf(types.get(i)), ((Number) counts.get(i)).intValue(), Integer::sum);
        }
        return observed;
    }
}
