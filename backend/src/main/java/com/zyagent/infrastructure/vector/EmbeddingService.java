package com.zyagent.infrastructure.vector;

import java.util.List;

public interface EmbeddingService {
    int dimension();

    List<Float> embed(String text);
}
