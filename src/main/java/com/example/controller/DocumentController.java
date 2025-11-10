package com.example.controller;

import com.example.domain.Document;
import com.example.service.CurrentUserService;
import com.example.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/docs")
public class DocumentController {
    private final DocumentService documentService;
    private final CurrentUserService currentUserService;

    public DocumentController(DocumentService documentService, CurrentUserService currentUserService) {
        this.documentService = documentService;
        this.currentUserService = currentUserService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/upload")
    public ResponseEntity<Document> upload(@RequestParam("title") String title,
                                           @RequestParam("file") MultipartFile file,
                                           Authentication auth) throws IOException {
        Long adminId = currentUserService.requireUserIdByUsername(auth.getName());
        Document doc = documentService.upload(adminId, title, file);
        return ResponseEntity.ok(doc);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) throws IOException {
        boolean ok = documentService.delete(id);
        return ok ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<Document>> list() {
        return ResponseEntity.ok(documentService.list());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<Document> get(@PathVariable("id") Long id) {
        Document doc = documentService.find(id);
        return doc == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(doc);
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}/content")
    public ResponseEntity<String> getContent(@PathVariable("id") Long id) throws IOException {
        String content = documentService.getDocumentContent(id);
        return content == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(content);
    }
}


