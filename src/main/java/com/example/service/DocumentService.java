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
        
        // 根据文件类型自动选择解析器并索引
        try {
            indexDocumentByType(dest, file.getOriginalFilename(), file.getContentType());
        } catch (Exception e) {
            // 记录错误但不影响文件上传
            org.slf4j.LoggerFactory.getLogger(DocumentService.class)
                .warn("Failed to index document: " + file.getOriginalFilename(), e);
        }
        
        return doc;
    }

    private void indexDocumentByType(Path filePath, String filename, String contentType) throws IOException {
        if (filename == null) {
            return;
        }
        
        String lowerFilename = filename.toLowerCase();
        
        // 根据文件扩展名和 content type 判断文件类型
        if (lowerFilename.endsWith(".pdf") || "application/pdf".equalsIgnoreCase(contentType)) {
            ragService.indexPdf(filePath);
        } else if (lowerFilename.endsWith(".txt") || "text/plain".equalsIgnoreCase(contentType)) {
            ragService.indexText(filePath);
        } else if (lowerFilename.endsWith(".md") || lowerFilename.endsWith(".markdown") || 
                   "text/markdown".equalsIgnoreCase(contentType)) {
            ragService.indexText(filePath);
        } else if (lowerFilename.endsWith(".html") || lowerFilename.endsWith(".htm") || 
                   "text/html".equalsIgnoreCase(contentType)) {
            ragService.indexHtml(filePath);
        } else if (lowerFilename.endsWith(".doc") || 
                   "application/msword".equalsIgnoreCase(contentType)) {
            ragService.indexWord(filePath);
        } else if (lowerFilename.endsWith(".docx") || 
                   "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(contentType)) {
            ragService.indexWord(filePath);
        }
        // 其他格式暂不支持索引，但文件可以上传
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


