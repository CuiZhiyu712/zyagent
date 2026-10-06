package com.zyagent.modules.job;

public record JobCollectResult(
    int added,
    int updated,
    int skipped,
    int failed
) {
    public JobCollectResult plus(JobCollectResult other) {
        return new JobCollectResult(
            added + other.added(),
            updated + other.updated(),
            skipped + other.skipped(),
            failed + other.failed()
        );
    }

    public static JobCollectResult empty() {
        return new JobCollectResult(0, 0, 0, 0);
    }
}
