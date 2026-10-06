package com.zyagent.modules.knowledgebase;

import java.util.ArrayList;
import java.util.List;

public class TextChunker {
    private final int chunkSize;
    private final int overlap;

    public TextChunker(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be >= 0 and < chunkSize");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<DocumentChunk> chunk(String documentId, String text) {
        String source = text == null ? "" : text;
        if (source.isBlank()) {
            return List.of();
        }
        List<DocumentChunk> chunks = new ArrayList<>();
        int start = 0;
        int index = 0;
        while (start < source.length()) {
            int end = Math.min(source.length(), start + chunkSize);
            chunks.add(new DocumentChunk(documentId, index++, source.substring(start, end)));
            if (end == source.length()) {
                break;
            }
            start = end - overlap;
        }
        return chunks;
    }
}
