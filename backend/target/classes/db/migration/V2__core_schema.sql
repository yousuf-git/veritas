-- Settlement reconciliation engine core schema.
-- Money is always (minor units BIGINT, ISO-4217 currency CHAR(3)). No floating point anywhere.

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(64)  NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(128) NOT NULL,
    role            VARCHAR(32)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT ck_users_role CHECK (role IN ('FINANCE_ANALYST', 'FINANCE_APPROVER', 'ADMIN'))
);

-- Internal ledger: orders, refunds and fees as they happened in our own system.
-- Append-only; the immutability trigger below enforces it at the database level.
CREATE TABLE ledger_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_type      VARCHAR(32)  NOT NULL,
    external_ref    VARCHAR(128) NOT NULL,
    provider_ref    VARCHAR(128),
    amount_minor    BIGINT       NOT NULL,
    currency        CHAR(3)      NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    description     VARCHAR(512),
    metadata        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_ledger_type CHECK (entry_type IN ('ORDER', 'REFUND', 'FEE', 'ADJUSTMENT')),
    CONSTRAINT ck_ledger_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_ledger_amount_nonzero CHECK (amount_minor <> 0),
    -- Sign convention: money in is positive, money out is negative. Enforced, not documented.
    CONSTRAINT ck_ledger_sign CHECK (
        (entry_type = 'ORDER' AND amount_minor > 0)
        OR (entry_type IN ('REFUND', 'FEE') AND amount_minor < 0)
        OR (entry_type = 'ADJUSTMENT')
    ),
    -- Re-posting the same business event is a no-op instead of a double-book.
    CONSTRAINT uq_ledger_type_ref UNIQUE (entry_type, external_ref)
);

CREATE INDEX ix_ledger_provider_ref ON ledger_entries (provider_ref) WHERE provider_ref IS NOT NULL;
CREATE INDEX ix_ledger_match_window ON ledger_entries (currency, occurred_at, amount_minor);
CREATE INDEX ix_ledger_occurred_at ON ledger_entries (occurred_at);

CREATE TABLE settlement_files (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename         VARCHAR(255) NOT NULL,
    provider         VARCHAR(32)  NOT NULL,
    checksum_sha256  CHAR(64)     NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    storage_path     VARCHAR(512) NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    line_count       INTEGER      NOT NULL DEFAULT 0,
    parse_error      VARCHAR(1024),
    uploaded_by      UUID         REFERENCES users (id),
    uploaded_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_file_provider CHECK (provider IN ('STRIPE', 'PAYPAL')),
    CONSTRAINT ck_file_status CHECK (status IN ('REGISTERED', 'PARSED', 'PARSE_FAILED')),
    CONSTRAINT ck_file_size CHECK (size_bytes > 0),
    -- Re-uploading a byte-identical file is rejected as a duplicate: idempotent ingestion.
    CONSTRAINT uq_file_checksum UNIQUE (checksum_sha256)
);

-- One raw provider row (Stripe balance transaction) exactly as delivered. Append-only.
CREATE TABLE settlement_lines (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id             UUID         NOT NULL REFERENCES settlement_files (id) ON DELETE CASCADE,
    line_number         INTEGER      NOT NULL,
    provider_txn_id     VARCHAR(128) NOT NULL,
    provider_ref        VARCHAR(128),
    txn_type            VARCHAR(48)  NOT NULL,
    gross_minor         BIGINT       NOT NULL,
    fee_minor           BIGINT       NOT NULL,
    net_minor           BIGINT       NOT NULL,
    currency            CHAR(3)      NOT NULL,
    created_at_provider TIMESTAMPTZ  NOT NULL,
    available_on        TIMESTAMPTZ,
    description         VARCHAR(512),
    raw                 JSONB        NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_line_currency CHECK (currency ~ '^[A-Z]{3}$'),
    -- Stripe's own invariant. If a file violates it, the file is wrong and we want to know immediately.
    CONSTRAINT ck_line_net_identity CHECK (net_minor = gross_minor - fee_minor),
    CONSTRAINT uq_line_file_number UNIQUE (file_id, line_number),
    CONSTRAINT uq_line_file_txn UNIQUE (file_id, provider_txn_id)
);

CREATE INDEX ix_line_file ON settlement_lines (file_id);
CREATE INDEX ix_line_provider_ref ON settlement_lines (provider_ref) WHERE provider_ref IS NOT NULL;
CREATE INDEX ix_line_txn_id ON settlement_lines (provider_txn_id);

CREATE TABLE reconciliation_runs (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id                  UUID        NOT NULL REFERENCES settlement_files (id) ON DELETE CASCADE,
    status                   VARCHAR(16) NOT NULL,
    triggered_by             UUID        REFERENCES users (id),
    batch_job_execution_id   BIGINT,
    total_lines              INTEGER     NOT NULL DEFAULT 0,
    matched_exact            INTEGER     NOT NULL DEFAULT 0,
    matched_heuristic        INTEGER     NOT NULL DEFAULT 0,
    unmatched                INTEGER     NOT NULL DEFAULT 0,
    discrepancy_count        INTEGER     NOT NULL DEFAULT 0,
    matched_amount_minor     BIGINT      NOT NULL DEFAULT 0,
    unmatched_amount_minor   BIGINT      NOT NULL DEFAULT 0,
    currency                 CHAR(3),
    error                    VARCHAR(1024),
    started_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at             TIMESTAMPTZ,
    CONSTRAINT ck_run_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED'))
);

-- At most one in-flight run per file: a second trigger cannot race a first into double-booking.
CREATE UNIQUE INDEX uq_run_active_per_file
    ON reconciliation_runs (file_id)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE INDEX ix_run_file ON reconciliation_runs (file_id);

-- The verdict for one settlement line in one run, including why it landed there.
CREATE TABLE match_results (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id              UUID          NOT NULL REFERENCES reconciliation_runs (id) ON DELETE CASCADE,
    settlement_line_id  UUID          NOT NULL REFERENCES settlement_lines (id) ON DELETE CASCADE,
    ledger_entry_id     UUID          REFERENCES ledger_entries (id),
    match_stage         VARCHAR(16)   NOT NULL,
    match_status        VARCHAR(16)   NOT NULL,
    confidence          NUMERIC(5, 4) NOT NULL DEFAULT 0,
    amount_delta_minor  BIGINT        NOT NULL DEFAULT 0,
    reason              VARCHAR(512)  NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_match_stage CHECK (match_stage IN ('EXACT', 'HEURISTIC', 'MANUAL', 'NONE')),
    CONSTRAINT ck_match_status CHECK (match_status IN ('MATCHED', 'PARTIAL', 'UNMATCHED', 'EXCLUDED')),
    CONSTRAINT ck_match_confidence CHECK (confidence >= 0 AND confidence <= 1),
    -- A result either names the ledger entry that backs it, or names no entry and no stage.
    -- EXCLUDED covers provider rows that are not customer transactions at all, such as payouts.
    CONSTRAINT ck_match_consistency CHECK (
        (match_status IN ('MATCHED', 'PARTIAL') AND ledger_entry_id IS NOT NULL AND match_stage <> 'NONE')
        OR (match_status IN ('UNMATCHED', 'EXCLUDED') AND ledger_entry_id IS NULL AND match_stage = 'NONE')
    ),
    CONSTRAINT uq_match_line_per_run UNIQUE (run_id, settlement_line_id)
);

-- The core anti-double-booking rule: one ledger entry can back at most one settlement line per run.
CREATE UNIQUE INDEX uq_match_ledger_per_run
    ON match_results (run_id, ledger_entry_id)
    WHERE ledger_entry_id IS NOT NULL;

CREATE INDEX ix_match_run ON match_results (run_id);

CREATE TABLE discrepancies (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id              UUID         NOT NULL REFERENCES reconciliation_runs (id) ON DELETE CASCADE,
    match_result_id     UUID         REFERENCES match_results (id) ON DELETE CASCADE,
    settlement_line_id  UUID         REFERENCES settlement_lines (id) ON DELETE CASCADE,
    ledger_entry_id     UUID         REFERENCES ledger_entries (id),
    type                VARCHAR(32)  NOT NULL,
    severity            VARCHAR(16)  NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    amount_impact_minor BIGINT       NOT NULL,
    currency            CHAR(3)      NOT NULL,
    detail              VARCHAR(1024) NOT NULL,
    assigned_to         UUID         REFERENCES users (id),
    version             INTEGER      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at         TIMESTAMPTZ,
    CONSTRAINT ck_disc_type CHECK (type IN (
        'MISSING_PAYOUT', 'MISSING_LEDGER_ENTRY', 'AMOUNT_DRIFT',
        'DUPLICATE_CHARGE', 'UNEXPECTED_FEE', 'FX_ROUNDING')),
    CONSTRAINT ck_disc_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_disc_status CHECK (status IN ('OPEN', 'IN_REVIEW', 'RESOLVED', 'ESCALATED')),
    CONSTRAINT ck_disc_currency CHECK (currency ~ '^[A-Z]{3}$'),
    -- A discrepancy is about a settlement line, a ledger entry, or both -- never neither.
    CONSTRAINT ck_disc_subject CHECK (settlement_line_id IS NOT NULL OR ledger_entry_id IS NOT NULL),
    CONSTRAINT ck_disc_resolved_at CHECK (
        (status = 'RESOLVED') = (resolved_at IS NOT NULL)
    )
);

CREATE INDEX ix_disc_queue ON discrepancies (status, severity, created_at);
CREATE INDEX ix_disc_run ON discrepancies (run_id);

-- Every human decision on the exception queue. Append-only.
CREATE TABLE discrepancy_resolutions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    discrepancy_id          UUID         NOT NULL REFERENCES discrepancies (id) ON DELETE CASCADE,
    action                  VARCHAR(32)  NOT NULL,
    linked_ledger_entry_id  UUID         REFERENCES ledger_entries (id),
    note                    VARCHAR(1024) NOT NULL,
    resolved_by             UUID         NOT NULL REFERENCES users (id),
    resolved_by_username    VARCHAR(64)  NOT NULL,
    resolved_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_res_action CHECK (action IN (
        'ACCEPT_FEE', 'LINK_MANUALLY', 'ESCALATE', 'WRITE_OFF', 'REJECT')),
    -- A manual link is meaningless without the entry it links to.
    CONSTRAINT ck_res_link CHECK (
        (action = 'LINK_MANUALLY') = (linked_ledger_entry_id IS NOT NULL)
    )
);

CREATE INDEX ix_res_discrepancy ON discrepancy_resolutions (discrepancy_id, resolved_at);

CREATE TABLE audit_log (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id        UUID        REFERENCES users (id),
    actor_username  VARCHAR(64) NOT NULL,
    action          VARCHAR(64) NOT NULL,
    entity_type     VARCHAR(64) NOT NULL,
    entity_id       VARCHAR(64) NOT NULL,
    detail          JSONB       NOT NULL DEFAULT '{}'::jsonb,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_entity ON audit_log (entity_type, entity_id, occurred_at DESC);
CREATE INDEX ix_audit_actor ON audit_log (actor_id, occurred_at DESC);

-- Append-only enforcement. Application bugs cannot silently rewrite money history.
CREATE OR REPLACE FUNCTION reject_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'table % is append-only; % is not permitted', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

-- Lines are never edited, but deleting the parent file must still be able to cascade,
-- so only UPDATE is blocked here.
CREATE TRIGGER trg_settlement_lines_no_update
    BEFORE UPDATE ON settlement_lines
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

CREATE TRIGGER trg_resolutions_no_update
    BEFORE UPDATE ON discrepancy_resolutions
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();

CREATE TRIGGER trg_audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION reject_mutation();
