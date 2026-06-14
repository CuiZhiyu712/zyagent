package com.zyagent.document;

import com.zyagent.job.TestAssertions;

import java.util.List;

public class TextChunkerTest {
    public static void run() {
        chunksTextWithOverlap();
    }

    private static void chunksTextWithOverlap() {
        TextChunker chunker = new TextChunker(10, 3);

        List<DocumentChunk> chunks = chunker.chunk("doc-1", "abcdefghijklmnopqrstuvwxyz");

        TestAssertions.equals(4, chunks.size(), "chunk size");
        TestAssertions.equals("abcdefghij", chunks.get(0).content(), "first chunk");
        TestAssertions.equals("hijklmnopq", chunks.get(1).content(), "overlapped second chunk");
    }
}
