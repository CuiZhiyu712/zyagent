package com.zyagent.agent;

import com.zyagent.job.TestAssertions;
import com.zyagent.storage.ChatMessageView;

import java.time.LocalDateTime;
import java.util.List;

public class ChatMemoryContextTest {
    public static void run() {
        keepsOnlyRecentMessagesForSameSession();
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
}
