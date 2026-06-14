package com.zyagent.tool;

import com.zyagent.document.DocumentSearchResponse;
import com.zyagent.document.KnowledgeType;

import java.util.List;

@FunctionalInterface
public interface KnowledgeSearchPort {
    DocumentSearchResponse search(String query, List<KnowledgeType> types);
}
