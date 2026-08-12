package com.reconengine.recon.batch;

import com.reconengine.ledger.LedgerEntryRepository;
import com.reconengine.recon.DiscrepancyRepository;
import com.reconengine.recon.MatchResultRepository;
import com.reconengine.recon.ReconciliationRunRepository;
import com.reconengine.recon.matching.LineOutcome;
import com.reconengine.recon.matching.RunMatchingContext;
import com.reconengine.recon.matching.TransactionMatcher;
import com.reconengine.settlement.SettlementLine;
import com.reconengine.settlement.SettlementLineRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.data.RepositoryItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The matching pipeline as three ordered steps: settle every provider line against the ledger,
 * then sweep the ledger for anything the provider never settled, then roll the verdicts up.
 * <p>
 * The job name is fixed but every run supplies a distinct {@code runId} parameter, so each
 * trigger is a new job instance rather than a restart of the previous one.
 */
@Configuration
public class ReconciliationJobConfig {

    public static final String JOB_NAME = "reconciliationJob";

    /** Small enough that a failure loses little work, large enough to keep round trips down. */
    private static final int CHUNK_SIZE = 100;

    @Bean
    public Job reconciliationJob(JobRepository jobRepository, Step matchSettlementLinesStep,
                                 Step detectMissingPayoutsStep, Step summariseRunStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(matchSettlementLinesStep)
                .next(detectMissingPayoutsStep)
                .next(summariseRunStep)
                .build();
    }

    @Bean
    public Step matchSettlementLinesStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                         RepositoryItemReader<SettlementLine> settlementLineReader,
                                         MatchingProcessor matchingProcessor,
                                         MatchResultWriter matchResultWriter) {
        return new ChunkOrientedStepBuilder<SettlementLine, LineOutcome>(
                "matchSettlementLines", jobRepository, CHUNK_SIZE)
                .reader(settlementLineReader)
                .processor(matchingProcessor)
                .writer(matchResultWriter)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    public Step detectMissingPayoutsStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                         MissingPayoutTasklet missingPayoutTasklet) {
        return new StepBuilder("detectMissingPayouts", jobRepository)
                .tasklet(missingPayoutTasklet, transactionManager)
                .build();
    }

    @Bean
    public Step summariseRunStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                 RunSummaryTasklet runSummaryTasklet) {
        return new StepBuilder("summariseRun", jobRepository)
                .tasklet(runSummaryTasklet, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public RepositoryItemReader<SettlementLine> settlementLineReader(
            SettlementLineRepository settlementLines,
            @Value("#{jobParameters['" + JobParams.FILE_ID + "']}") String fileId) {

        RepositoryItemReader<SettlementLine> reader = new RepositoryItemReader<>(
                settlementLines, Map.of("lineNumber", Sort.Direction.ASC));
        reader.setMethodName("findByFileIdOrderByLineNumber");
        reader.setArguments(List.of(UUID.fromString(fileId)));
        reader.setPageSize(CHUNK_SIZE);
        reader.setName("settlementLineReader");
        return reader;
    }

    @Bean
    @StepScope
    public RunMatchingContext runMatchingContext(
            @Value("#{jobParameters['" + JobParams.RUN_ID + "']}") String runId,
            @Value("#{jobParameters['" + JobParams.WINDOW_START_EPOCH + "']}") Long windowStart,
            @Value("#{jobParameters['" + JobParams.WINDOW_END_EPOCH + "']}") Long windowEnd) {

        return new RunMatchingContext(UUID.fromString(runId),
                Instant.ofEpochMilli(windowStart), Instant.ofEpochMilli(windowEnd));
    }

    @Bean
    @StepScope
    public MatchingProcessor matchingProcessor(TransactionMatcher matcher, RunMatchingContext runMatchingContext) {
        return new MatchingProcessor(matcher, runMatchingContext);
    }

    @Bean
    @StepScope
    public MatchResultWriter matchResultWriter(
            MatchResultRepository matchResults, DiscrepancyRepository discrepancies,
            @Value("#{jobParameters['" + JobParams.RUN_ID + "']}") String runId) {

        return new MatchResultWriter(matchResults, discrepancies, UUID.fromString(runId));
    }

    @Bean
    @StepScope
    public MissingPayoutTasklet missingPayoutTasklet(
            LedgerEntryRepository ledgerEntries, DiscrepancyRepository discrepancies,
            @Value("#{jobParameters['" + JobParams.RUN_ID + "']}") String runId,
            @Value("#{jobParameters['" + JobParams.WINDOW_START_EPOCH + "']}") Long windowStart,
            @Value("#{jobParameters['" + JobParams.WINDOW_END_EPOCH + "']}") Long windowEnd,
            @Value("#{jobParameters['" + JobParams.CURRENCIES + "']}") String currencies) {

        return new MissingPayoutTasklet(ledgerEntries, discrepancies, UUID.fromString(runId),
                Instant.ofEpochMilli(windowStart), Instant.ofEpochMilli(windowEnd),
                Arrays.stream(currencies.split(",")).filter(c -> !c.isBlank()).toList());
    }

    @Bean
    @StepScope
    public RunSummaryTasklet runSummaryTasklet(
            ReconciliationRunRepository runs, MatchResultRepository matchResults,
            DiscrepancyRepository discrepancies, SettlementLineRepository settlementLines, Clock clock,
            @Value("#{jobParameters['" + JobParams.RUN_ID + "']}") String runId,
            @Value("#{jobParameters['" + JobParams.FILE_ID + "']}") String fileId) {

        return new RunSummaryTasklet(runs, matchResults, discrepancies, settlementLines, clock,
                UUID.fromString(runId), UUID.fromString(fileId));
    }
}
