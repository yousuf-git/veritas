package com.reconengine.recon.batch;

import com.reconengine.recon.matching.LineOutcome;
import com.reconengine.recon.matching.RunMatchingContext;
import com.reconengine.recon.matching.TransactionMatcher;
import com.reconengine.settlement.SettlementLine;
import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * Step-scoped so the claim registry inside the context lives exactly as long as one run and is
 * never shared between concurrent runs of different files.
 */
public class MatchingProcessor implements ItemProcessor<SettlementLine, LineOutcome> {

    private final TransactionMatcher matcher;
    private final RunMatchingContext context;

    public MatchingProcessor(TransactionMatcher matcher, RunMatchingContext context) {
        this.matcher = matcher;
        this.context = context;
    }

    @Override
    public LineOutcome process(SettlementLine line) {
        return matcher.match(line, context);
    }
}
