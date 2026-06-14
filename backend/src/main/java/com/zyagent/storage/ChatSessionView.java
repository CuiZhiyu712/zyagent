package com.zyagent.storage;

import java.time.LocalDateTime;

public record ChatSessionView(
    String id,
    String title,
    String agentMode,
    int count,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
