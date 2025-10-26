package com.example.service;

import com.example.domain.Document;
import com.example.mapper.DocumentMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {
    private final DocumentMapper documentMapper;
    private final RagService ragService;

    @Value("${app.rag.upload-dir}")
    private String uploadDir;

    public DocumentService(DocumentMapper documentMapper, RagService ragService) {
        this.documentMapper = documentMapper;
        this.ragService = ragService;
    }

    public Document upload(Long userId, String title, MultipartFile file) throws IOException {
        Path root = Paths.get(uploadDir);
        Files.createDirectories(root);
        String storedName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path dest = root.resolve(storedName);
        file.transferTo(dest.toFile());

        Document doc = new Document();
        doc.setTitle(title);
        doc.setFilename(file.getOriginalFilename());
        doc.setContentType(file.getContentType());
        doc.setSizeBytes(file.getSize());
        doc.setStoragePath(dest.toAbsolutePath().toString());
        doc.setCreatedBy(userId);
        documentMapper.insert(doc);
        if ("application/pdf".equalsIgnoreCase(file.getContentType())) {
            ragService.indexPdf(dest);
        }
        return doc;
    }

    public boolean delete(Long id) throws IOException {
        Document doc = documentMapper.findById(id);
        if (doc == null) return false;
        if (doc.getStoragePath() != null) {
            FileSystemUtils.deleteRecursively(Path.of(doc.getStoragePath()));
        }
        return documentMapper.deleteById(id) > 0;
    }

    public List<Document> list() {
        return documentMapper.listAll();
    }

    public Document find(Long id) {
        return documentMapper.findById(id);
    }
}


