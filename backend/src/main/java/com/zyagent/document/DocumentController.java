package com.zyagent.document;

import com.zyagent.common.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/files/upload")
    public ApiResponse<DocumentRecord> upload(@RequestParam MultipartFile file, @RequestParam(defaultValue = "STUDY") KnowledgeType knowledgeType) {
        return ApiResponse.ok(documentService.upload(file, knowledgeType));
    }

    @DeleteMapping("/documents/{documentId}")
    public ApiResponse<Void> delete(@PathVariable String documentId) {
        try {
            boolean deleted = documentService.delete(documentId);
            return deleted ? ApiResponse.ok(null) : ApiResponse.fail("文档不存在");
        } catch (RuntimeException ex) {
            return ApiResponse.fail(ex.getMessage());
        }
    }

    @GetMapping("/documents")
    public ApiResponse<List<DocumentRecord>> list() {
        return ApiResponse.ok(documentService.list());
    }
}
