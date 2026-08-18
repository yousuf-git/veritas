package com.veritas.provider.generator;

import com.veritas.settlement.SettlementDtos;
import com.veritas.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/demo")
@Tag(name = "Demo data")
public class DemoController {

    private final DemoScenarioService scenarios;

    public DemoController(DemoScenarioService scenarios) {
        this.scenarios = scenarios;
    }

    @PostMapping("/scenario")
    @PreAuthorize("hasAuthority('" + Role.Permissions.LEDGER_WRITE + "')")
    @Operation(summary = "Seed a reproducible ledger and provider file with known discrepancies")
    public ScenarioResponse generate(@Valid @RequestBody ScenarioRequest request) {
        DemoScenarioService.Result result = scenarios.generate(request);
        return new ScenarioResponse(
                SettlementDtos.FileResponse.from(result.file(), result.fileCreated()),
                result.ledgerEntriesCreated(),
                result.expectedDiscrepancies());
    }

    public record ScenarioResponse(SettlementDtos.FileResponse file, int ledgerEntriesCreated,
                                   Map<String, Integer> expectedDiscrepancies) {
    }
}
