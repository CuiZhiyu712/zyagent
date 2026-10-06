package com.zyagent.modules.job;

import java.time.LocalDateTime;

public record JobCollectLog(
    String id,
    String sourceId,
    String sourceName,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    String status,
    int added,
    int updated,
    int skipped,
    int failed,
    String errorMessage,
    int searched,
    int detailFetched,
    int detailFailed
) {
    public JobCollectLog(
        String id,
        String sourceId,
        String sourceName,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String status,
        int added,
        int updated,
        int skipped,
        int failed,
        String errorMessage
    ) {
        this(id, sourceId, sourceName, startedAt, finishedAt, status, added, updated, skipped, failed, errorMessage, 0, 0, 0);
    }
}
