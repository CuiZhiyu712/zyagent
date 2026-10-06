package com.zyagent.infrastructure.vector;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class HashEmbeddingService implements EmbeddingService {
    private static final int DIMENSION = 128;

    @Override
    public int dimension() {
        return DIMENSION;
    }

    @Override
    public List<Float> embed(String text) {
        float[] vector = new float[DIMENSION];
        byte[] bytes = (text == null ? "" : text).toLowerCase().getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            int bucket = Byte.toUnsignedInt(bytes[i]) * 31 + i * 17;
            vector[Math.floorMod(bucket, DIMENSION)] += 1.0f;
        }
        float norm = 0.0f;
        for (float value : vector) {
            norm += value * value;
        }
        norm = (float) Math.sqrt(norm);
        List<Float> result = new ArrayList<>(DIMENSION);
        for (float value : vector) {
            result.add(norm == 0.0f ? 0.0f : value / norm);
        }
        return result;
    }
}
