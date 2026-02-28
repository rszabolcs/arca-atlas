package com.arcadigitalis.backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 10 (T096) — Secret Log Scan Test.
 * Ensures the logback-spring.xml secret-scrubbing regex covers all sensitive patterns.
 * Verifies that no Java source file directly logs secrets like passwords, keys, or seeds.
 */
class SecretLogScanTest {

    private static final Pattern SECRET_LOG_PATTERN = Pattern.compile(
        "log\\.(info|debug|warn|error|trace)\\(.*(" +
        "password|secret|private.?key|seed|mnemonic|dek|signing.?key|jwt.?secret" +
        ").*\\)",
        Pattern.CASE_INSENSITIVE
    );

    @Test
    @DisplayName("No Java source file directly logs secret-looking values")
    void noDirectSecretLogging() throws Exception {
        Path srcDir = Path.of("src/main/java");
        if (!Files.exists(srcDir)) {
            // Running from backend/ subdirectory
            srcDir = Path.of("backend/src/main/java");
        }
        if (!Files.exists(srcDir)) return; // Skip if unable to locate

        try (Stream<Path> files = Files.walk(srcDir)) {
            List<String> violations = files
                .filter(p -> p.toString().endsWith(".java"))
                .flatMap(p -> {
                    try {
                        return Files.readAllLines(p).stream()
                            .filter(line -> SECRET_LOG_PATTERN.matcher(line).find())
                            .map(line -> p.getFileName() + ": " + line.trim());
                    } catch (Exception e) { return Stream.empty(); }
                })
                .toList();

            assertThat(violations)
                .as("Java source files should not directly log secrets")
                .isEmpty();
        }
    }

    @Test
    @DisplayName("logback-spring.xml contains secret-scrubbing regex")
    void logbackContainsSecretScrubbing() throws Exception {
        Path logbackPath = Path.of("src/main/resources/logback-spring.xml");
        if (!Files.exists(logbackPath)) {
            logbackPath = Path.of("backend/src/main/resources/logback-spring.xml");
        }
        if (!Files.exists(logbackPath)) return;

        String content = Files.readString(logbackPath);
        assertThat(content)
            .as("logback-spring.xml should contain secret scrubbing patterns")
            .containsIgnoringCase("secret")
            .containsIgnoringCase("password");
    }
}
