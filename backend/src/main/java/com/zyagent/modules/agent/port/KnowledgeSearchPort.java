package com.zyagent.modules.agent.port;

import com.zyagent.modules.knowledgebase.DocumentSearchResponse;
import com.zyagent.modules.knowledgebase.KnowledgeType;

import java.util.List;

/** Agent-facing port for retrieving user-owned knowledge with citations. */
@FunctionalInterface
public interface KnowledgeSearchPort {
    DocumentSearchResponse search(String query, List<KnowledgeType> types);
}
