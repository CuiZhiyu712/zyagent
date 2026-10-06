package com.zyagent.modules.agent;

import com.zyagent.modules.job.TestAssertions;
import com.zyagent.infrastructure.storage.ChatMessageView;

import java.time.LocalDateTime;
import java.util.List;

public class ChatMemoryContextTest {
    public static void run() {
        keepsOnlyRecentMessagesForSameSession();
        createsMemorySnapshotForUi();
    }

    private static void keepsOnlyRecentMessagesForSameSession() {
        ChatMemoryContext context = new ChatMemoryContext(2);

        String prompt = context.render(List.of(
            message("user", "第一轮问题"),
            message("assistant", "第一轮回答"),
            message("user", "第二轮问题")
        ));

        TestAssertions.isTrue(!prompt.contains("第一轮问题"), "oldest message trimmed");
        TestAssertions.isTrue(prompt.contains("第一轮回答"), "recent assistant message kept");
        TestAssertions.isTrue(prompt.contains("第二轮问题"), "recent user message kept");
    }

    private static ChatMessageView message(String role, String content) {
        return new ChatMessageView("id-" + content, "session-1", role, content, null, LocalDateTime.now());
    }

    private static void createsMemorySnapshotForUi() {
        ChatMemoryContext context = new ChatMemoryContext(2);

        ChatMemorySnapshot snapshot = context.snapshot(List.of(
            message("user", "first"),
            message("assistant", "second"),
            message("user", "third")
        ));

        TestAssertions.equals(2, snapshot.recentMessageCount(), "recent message count");
        TestAssertions.isTrue(snapshot.summary().contains("second"), "snapshot keeps assistant message");
        TestAssertions.isTrue(snapshot.summary().contains("third"), "snapshot keeps user message");
    }
}
