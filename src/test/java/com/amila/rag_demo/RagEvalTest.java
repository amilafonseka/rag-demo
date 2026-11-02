package com.amila.rag_demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RagEvalTest {

    @Autowired
    RagService ragService;

    @Test
    void evaluateAnswers() throws Exception {
        List<String> lines = Files.readAllLines(
                new ClassPathResource("eval-questions.csv").getFile().toPath(), StandardCharsets.UTF_8);

        int passed = 0, total = 0;
        for (String line : lines.subList(1, lines.size())) {          // skip the header row
            if (line.isBlank()) continue;
            int comma = line.lastIndexOf(',');                         // last comma, in case a question has one
            String question = line.substring(0, comma).trim();
            String expected = line.substring(comma + 1).trim();

            String answer = ragService.ask(question);
            boolean saidIdk = answer.toLowerCase().contains("i don't know");
            boolean ok = expected.equals("IDK") ? saidIdk : !saidIdk;

            total++;
            if (ok) passed++;
            System.out.printf("%s  [%s]  %s%n", ok ? "PASS" : "FAIL", expected, question);
        }

        System.out.printf("%nSCORE: %d/%d%n", passed, total);
        assertTrue(passed >= total * 0.8, "Eval score below 80%");
    }
}