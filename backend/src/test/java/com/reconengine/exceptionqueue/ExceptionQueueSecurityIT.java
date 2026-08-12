package com.reconengine.exceptionqueue;

import com.jayway.jsonpath.JsonPath;
import com.reconengine.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The authorization matrix that actually matters: who may see the queue, and who may write money off. */
class ExceptionQueueSecurityIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("the queue rejects anonymous callers")
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/v1/exceptions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ledger/entries")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/runs").param("fileId", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a garbage bearer token is rejected rather than ignored")
    void invalidTokenIsRejected() throws Exception {
        mvc.perform(get("/api/v1/exceptions").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an analyst cannot post to the ledger; an admin can")
    void ledgerWriteIsRestrictedToAdmin() throws Exception {
        String entry = """
                {"entryType":"ORDER","externalRef":"ORD-SEC-1","providerRef":"ch_sec_1",
                 "amountMinor":5000,"currency":"USD","occurredAt":"2026-02-01T10:00:00Z"}
                """;

        mvc.perform(post("/api/v1/ledger/entries")
                        .header("Authorization", tokenFor("analyst"))
                        .contentType("application/json").content(entry))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/ledger/entries")
                        .header("Authorization", tokenFor("admin"))
                        .contentType("application/json").content(entry))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("writing an exception off needs an approver, though any analyst may resolve it otherwise")
    void writeOffRequiresApprover() throws Exception {
        String admin = tokenFor("admin");
        String exceptionId = firstOpenException(admin);

        String writeOff = """
                {"action":"WRITE_OFF","note":"Accepting the loss on this one."}
                """;

        mvc.perform(post("/api/v1/exceptions/{id}/resolve", exceptionId)
                        .header("Authorization", tokenFor("analyst"))
                        .contentType("application/json").content(writeOff))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/exceptions/{id}/resolve", exceptionId)
                        .header("Authorization", tokenFor("approver"))
                        .contentType("application/json").content(writeOff))
                .andExpect(status().isOk());

        // Resolving twice is a conflict, not a silent second resolution.
        mvc.perform(post("/api/v1/exceptions/{id}/resolve", exceptionId)
                        .header("Authorization", tokenFor("approver"))
                        .contentType("application/json").content(writeOff))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a decision must say why")
    void resolutionRequiresANote() throws Exception {
        String admin = tokenFor("admin");
        String exceptionId = firstOpenException(admin);

        mvc.perform(post("/api/v1/exceptions/{id}/resolve", exceptionId)
                        .header("Authorization", tokenFor("analyst"))
                        .contentType("application/json")
                        .content("""
                                {"action":"REJECT","note":"  "}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    /** Produces a small scenario and returns one open exception from it. */
    private String firstOpenException(String adminToken) throws Exception {
        String scenario = mvc.perform(post("/api/v1/demo/scenario")
                        .header("Authorization", adminToken)
                        .contentType("application/json")
                        .content("""
                                {"transactions": 15, "seed": %d, "currency": "GBP",
                                 "missingPayouts": 2, "missingLedgerEntries": 1, "amountDrifts": 1,
                                 "fxRoundings": 0, "duplicateCharges": 0, "unexpectedFees": 0,
                                 "heuristicOnly": 0}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String fileId = JsonPath.read(scenario, "$.file.id");

        String triggered = mvc.perform(post("/api/v1/runs")
                        .header("Authorization", adminToken)
                        .param("fileId", fileId))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String runId = JsonPath.read(triggered, "$.id");

        String queue = awaitUntil(Duration.ofSeconds(90),
                () -> readQueue(adminToken, runId),
                body -> ((Number) JsonPath.read(body, "$.totalItems")).intValue() > 0);

        assertThat((String) JsonPath.read(queue, "$.items[0].status")).isEqualTo("OPEN");
        return JsonPath.read(queue, "$.items[0].id");
    }

    private String readQueue(String token, String runId) {
        try {
            return mvc.perform(get("/api/v1/exceptions")
                            .param("runId", runId).param("status", "OPEN")
                            .header("Authorization", token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to read exception queue", ex);
        }
    }
}
