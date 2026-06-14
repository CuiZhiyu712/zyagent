package com.zyagent.chat;

import java.util.ArrayList;
import java.util.List;

public final class StreamChunker {
    private StreamChunker() {
    }

    public static List<String> chunk(String text, int size) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + size);
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }
}
