package com.example.bootstrap;

import com.example.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class RagBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RagBootstrap.class);

    private final RagService ragService;

    @Value("${app.rag.upload-dir}")
    private String uploadDir;

    public RagBootstrap(RagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Path root = Paths.get(uploadDir);
        if (!Files.exists(root)) {
            log.warn("[RagBootstrap] upload dir not exists: {}", root);
            return;
        }
        log.info("[RagBootstrap] rebuilding index from: {}", root);
        try {
            Files.walk(root)
                .filter(Files::isRegularFile)
                .forEach(p -> {
                    try {
                        indexFileByType(p);
                        log.info("[RagBootstrap] indexed: {}", p);
                    } catch (Exception e) {
                        // 捕获所有异常，包括 API 错误，不阻止应用启动
                        log.warn("[RagBootstrap] index failed: {} - {}", p, e.getMessage());
                        log.debug("[RagBootstrap] index error details", e);
                    }
                });
        } catch (IOException e) {
            log.warn("[RagBootstrap] scan failed: {}", e.getMessage());
        } catch (Exception e) {
            // 捕获其他异常，确保应用可以启动
            log.warn("[RagBootstrap] unexpected error during indexing: {}", e.getMessage());
            log.debug("[RagBootstrap] unexpected error details", e);
        }
    }

    /**
     * 根据文件类型调用相应的索引方法
     */
    private void indexFileByType(Path filePath) throws IOException {
        String filename = filePath.getFileName().toString().toLowerCase();
        
        // 根据文件扩展名判断文件类型并索引
        if (filename.endsWith(".pdf")) {
            ragService.indexPdf(filePath);
        } else if (filename.endsWith(".txt") || filename.endsWith(".md") || filename.endsWith(".markdown")) {
            ragService.indexText(filePath);
        } else if (filename.endsWith(".html") || filename.endsWith(".htm")) {
            ragService.indexHtml(filePath);
        } else if (filename.endsWith(".doc") || filename.endsWith(".docx")) {
            ragService.indexWord(filePath);
        } else {
            log.debug("[RagBootstrap] skipping unsupported file: {}", filename);
        }
    }
}


