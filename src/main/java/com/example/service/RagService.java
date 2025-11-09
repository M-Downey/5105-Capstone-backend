package com.example.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.ArrayList;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.stereotype.Service;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RagService {
    private final ChatLanguageModel chatModel;
    private final StreamingChatLanguageModel streamingChatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    public RagService(ChatLanguageModel chatModel, StreamingChatLanguageModel streamingChatModel, EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    public void indexPdf(Path pdfPath) throws IOException {
        long t0 = System.currentTimeMillis();
        byte[] bytes = Files.readAllBytes(pdfPath);
        ApachePdfBoxDocumentParser parser = new ApachePdfBoxDocumentParser();
        Document doc = parser.parse(new ByteArrayInputStream(bytes));
        indexDocument(doc, pdfPath, bytes.length, t0, "PDF");
    }

    public void indexText(Path textPath) throws IOException {
        long t0 = System.currentTimeMillis();
        String content = Files.readString(textPath, StandardCharsets.UTF_8);
        Document doc = Document.from(content);
        byte[] bytes = Files.readAllBytes(textPath);
        indexDocument(doc, textPath, bytes.length, t0, "TEXT");
    }

    public void indexWord(Path wordPath) throws IOException {
        long t0 = System.currentTimeMillis();
        String content;
        byte[] bytes = Files.readAllBytes(wordPath);
        
        String fileName = wordPath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".docx")) {
            // 处理 .docx 文件
            try (FileInputStream fis = new FileInputStream(wordPath.toFile());
                 XWPFDocument document = new XWPFDocument(fis);
                 XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                content = extractor.getText();
            }
        } else if (fileName.endsWith(".doc")) {
            // 处理 .doc 文件
            try (FileInputStream fis = new FileInputStream(wordPath.toFile());
                 HWPFDocument document = new HWPFDocument(fis);
                 WordExtractor extractor = new WordExtractor(document)) {
                content = extractor.getText();
            }
        } else {
            throw new IllegalArgumentException("Unsupported Word format: " + fileName);
        }
        
        Document doc = Document.from(content);
        indexDocument(doc, wordPath, bytes.length, t0, "WORD");
    }

    public void indexHtml(Path htmlPath) throws IOException {
        long t0 = System.currentTimeMillis();
        // 读取 HTML 文件内容
        String htmlContent = Files.readString(htmlPath, StandardCharsets.UTF_8);
        // 简单的 HTML 标签移除（可以后续优化为使用专门的 HTML 解析器）
        String textContent = htmlContent.replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        Document doc = Document.from(textContent);
        byte[] bytes = Files.readAllBytes(htmlPath);
        indexDocument(doc, htmlPath, bytes.length, t0, "HTML");
    }

    private void indexDocument(Document doc, Path filePath, long fileSize, long startTime, String fileType) {
        DocumentSplitter splitter = DocumentSplitters.recursive(1000, 100);
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
        ingestor.ingest(doc);
        long dt = System.currentTimeMillis() - startTime;
        log.info("[RagService] indexed {}, path={}, bytes={}, costMs={}", fileType, filePath, fileSize, dt);
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

    public String buildPrompt(String userMessage) {
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
        log.debug("[RagService] prompt built, queryLen={}, hits={}", userMessage == null ? 0 : userMessage.length(), hit);
        return prompt;
    }

    public void chatWithRagStreaming(String userMessage, StreamingResponseHandler<AiMessage> handler) {
        long t0 = System.currentTimeMillis();
        String prompt = buildPrompt(userMessage);
        
        // 构建 ChatMessage 列表
        List<ChatMessage> messages = new ArrayList<>();
        // 将 prompt 作为系统消息，userMessage 作为用户消息
        String[] lines = prompt.split("\nUser: ");
        if (lines.length == 2) {
            String systemPrompt = lines[0];
            String userContent = lines[1];
            messages.add(SystemMessage.from(systemPrompt));
            messages.add(UserMessage.from(userContent));
        } else {
            // 如果格式不符合预期，将整个 prompt 作为用户消息
            messages.add(UserMessage.from(prompt));
        }
        
        streamingChatModel.generate(messages, handler);
        long dt = System.currentTimeMillis() - t0;
        log.info("[RagService] chatWithRagStreaming done, queryLen={}, costMs={}", userMessage == null ? 0 : userMessage.length(), dt);
    }
}


