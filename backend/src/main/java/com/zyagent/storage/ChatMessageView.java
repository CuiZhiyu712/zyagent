package com.zyagent.storage;

import java.time.LocalDateTime;

public record ChatMessageView(
    String id,
    String sessionId,
    String role,
    String content,
    String referencesJson,
    LocalDateTime createdAt
) {
}
