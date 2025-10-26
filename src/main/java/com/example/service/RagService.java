package com.example.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.ArrayList;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RagService {
    private final ChatLanguageModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    public RagService(ChatLanguageModel chatModel, EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    public void indexPdf(Path pdfPath) throws IOException {
        long t0 = System.currentTimeMillis();
        byte[] bytes = Files.readAllBytes(pdfPath);
        ApachePdfBoxDocumentParser parser = new ApachePdfBoxDocumentParser();
        Document doc = parser.parse(new ByteArrayInputStream(bytes));
        DocumentSplitter splitter = DocumentSplitters.recursive(1000, 100);
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
        ingestor.ingest(doc);
        long dt = System.currentTimeMillis() - t0;
        log.info("[RagService] indexed PDF, path={}, bytes={}, costMs={}", pdfPath, bytes.length, dt);
    }

    public String chatWithRag(String userMessage) {
        long t0 = System.currentTimeMillis();
        Embedding userEmbedding = embeddingModel.embed(userMessage).content();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(userEmbedding, 4);
        StringBuilder context = new StringBuilder();
        int hit = 0;
        if (matches != null) {
            for (EmbeddingMatch<TextSegment> match : matches) {
                if (match.embedded() != null) {
                    context.append(match.embedded().text()).append("\n");
                    hit++;
                }
            }
        }
        String prompt = "Answer the question based on the following knowledge context.\n" +
                context +
                "\nUser: " + userMessage;
        String resp = chatModel.generate(prompt);
        long dt = System.currentTimeMillis() - t0;
        log.info("[RagService] chatWithRag done, queryLen={}, hits={}, costMs={}", userMessage == null ? 0 : userMessage.length(), hit, dt);
        return resp;
    }
}


