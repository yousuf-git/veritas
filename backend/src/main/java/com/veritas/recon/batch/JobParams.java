package com.veritas.recon.batch;

/** Job parameter names, shared between the service that launches a run and the step beans. */
public final class JobParams {

    public static final String RUN_ID = "runId";
    public static final String FILE_ID = "fileId";
    public static final String WINDOW_START_EPOCH = "windowStartEpochMilli";
    public static final String WINDOW_END_EPOCH = "windowEndEpochMilli";
    public static final String CURRENCIES = "currencies";

    private JobParams() {
    }
}
