package com.zyagent.modules.chat;

import com.zyagent.modules.job.TestAssertions;

import java.util.List;

public class StreamChunkerTest {
    public static void run() {
        splitsLongAnswerIntoSmallReadableChunks();
    }

    private static void splitsLongAnswerIntoSmallReadableChunks() {
        List<String> chunks = StreamChunker.chunk("abcdefghijklmnopqrstuvwxyz", 8);

        TestAssertions.equals(4, chunks.size(), "chunk count");
        TestAssertions.equals("abcdefgh", chunks.get(0), "first chunk");
        TestAssertions.equals("yz", chunks.get(3), "last chunk");
    }
}
