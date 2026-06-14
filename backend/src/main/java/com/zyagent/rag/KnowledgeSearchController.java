package com.zyagent.rag;

import com.zyagent.common.ApiResponse;
import com.zyagent.document.DocumentSearchResponse;
import com.zyagent.document.DocumentService;
import com.zyagent.document.KnowledgeType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeSearchController {
    private final DocumentService documentService;

    public KnowledgeSearchController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/search")
    public ApiResponse<DocumentSearchResponse> search(@RequestBody KnowledgeSearchRequest request) {
        return ApiResponse.ok(documentService.searchWithReferences(request.query(), request.types()));
    }

    public record KnowledgeSearchRequest(String query, List<KnowledgeType> types) {
    }
}
