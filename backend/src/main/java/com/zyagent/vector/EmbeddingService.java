package com.zyagent.vector;

import java.util.List;

public interface EmbeddingService {
    int dimension();

    List<Float> embed(String text);
}
