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
                .filter(p -> Files.isRegularFile(p) && p.toString().toLowerCase().endsWith(".pdf"))
                .forEach(p -> {
                    try {
                        ragService.indexPdf(p);
                        log.info("[RagBootstrap] indexed: {}", p);
                    } catch (IOException e) {
                        log.warn("[RagBootstrap] index failed: {} - {}", p, e.getMessage());
                    }
                });
        } catch (IOException e) {
            log.warn("[RagBootstrap] scan failed: {}", e.getMessage());
        }
    }
}


