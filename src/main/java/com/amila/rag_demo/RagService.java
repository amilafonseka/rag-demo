package com.amila.rag_demo;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

@Service
public class RagService implements ApplicationRunner {

    private final SimpleVectorStore vectorStore;
    private final ChatClient chatClient;

    public RagService(EmbeddingModel embeddingModel, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        this.chatClient = chatClientBuilder.build();
    }

    // 1. INGEST: runs once at startup: read PDFs -> split into chunks -> embed -> store
    @Override
    public void run(ApplicationArguments args) throws Exception {
        Resource[] pdfs = new PathMatchingResourcePatternResolver()
                .getResources("classpath:docs/*.pdf");
        for (Resource pdf : pdfs) {
            List<Document> pages = new PagePdfDocumentReader(pdf).get();
            List<Document> chunks = new TokenTextSplitter().apply(pages);
            vectorStore.add(chunks);
            System.out.println("Ingested " + pdf.getFilename() + ": " + chunks.size() + " chunks");
        }
    }

    // 2. RETRIEVE + 3. GENERATE
    public String ask(String question) {
        List<Document> matches = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(4).similarityThreshold(0.45).build());

        if (matches.isEmpty()) {
            return "I don't know based on the provided documents";
        }

        matches.forEach(d -> System.out.println("score=" + d.getScore()
        + "  text=" + d.getText().substring(0, Math.min(60, d.getText().length()))));

        String context = matches.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        return chatClient.prompt()
                .system("""
                        Answer ONLY using the context below.
                        If the answer is not in the context, say "I don't know".
                        Context:
                        """ + context)
                .user(question)
                .call()
                .content();
    }
}