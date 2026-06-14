package com.zyagent.job;

import java.util.List;
import java.util.Optional;

public interface JobCollectorStore {
    List<JobSource> listSources();

    JobSource saveSource(JobSource source);

    void updateSourceStatus(String sourceId, String status);

    UpsertResult upsertCollectedJob(JobPosting posting, String sourceName, String externalId, String contentHash);

    void saveLog(JobCollectLog log);

    List<JobCollectLog> listLogs();

    Optional<JobSource> findSource(String id);

    default int deleteInvalidCollectedJobs() {
        return 0;
    }

    enum UpsertResult {
        ADDED,
        UPDATED,
        SKIPPED
    }
}
