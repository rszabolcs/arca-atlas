package com.arcadigitalis.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture tests — T107.
 * (1) api package has zero imports from persistence package.
 * (2) No static mutable fields in service-layer classes.
 */
class ArchitectureTest {

    private static final Pattern PERSISTENCE_IMPORT = Pattern.compile(
        "import\\s+com\\.arcadigitalis\\.backend\\.persistence\\.");

    private static final Pattern STATIC_MUTABLE_FIELD = Pattern.compile(
        "\\bstatic\\s+(?!final\\b)\\w+\\s+\\w+");

    @Test
    @DisplayName("api package has zero imports from persistence package (Constitution Quality Bar)")
    void apiLayerNoPersistenceImports() throws IOException {
        Path apiDir = resolveSourceDir("api");
        if (apiDir == null || !Files.exists(apiDir)) return;

        try (Stream<Path> files = Files.walk(apiDir)) {
            List<String> violations = files
                .filter(p -> p.toString().endsWith(".java"))
                .flatMap(p -> {
                    try {
                        return Files.readAllLines(p).stream()
                            .filter(line -> PERSISTENCE_IMPORT.matcher(line).find())
                            .map(line -> p.getFileName() + ": " + line.trim());
                    } catch (Exception e) { return Stream.empty(); }
                })
                .toList();

            assertThat(violations)
                .as("api package should not import from persistence package")
                .isEmpty();
        }
    }

    @Test
    @DisplayName("No static mutable fields in service-layer classes (NFR-003)")
    void noStaticMutableFieldsInServiceLayer() throws IOException {
        String[] packages = {"policy", "evm", "storage", "notifications"};

        for (String pkg : packages) {
            Path dir = resolveSourceDir(pkg);
            if (dir == null || !Files.exists(dir)) continue;

            try (Stream<Path> files = Files.walk(dir)) {
                List<String> violations = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().contains("Test"))
                    .flatMap(p -> {
                        try {
                            return Files.readAllLines(p).stream()
                                .map(String::trim)
                                .filter(line -> !line.startsWith("//") && !line.startsWith("*"))
                                .filter(line -> STATIC_MUTABLE_FIELD.matcher(line).find())
                                // Allow known safe patterns
                                .filter(line -> !line.contains("Logger"))
                                .filter(line -> !line.contains("AtomicLong"))
                                .filter(line -> !line.contains("AtomicInteger"))
                                .filter(line -> !line.contains("AtomicBoolean"))
                                .filter(line -> !line.contains("AtomicReference"))
                                .map(line -> p.getFileName() + ": " + line);
                        } catch (Exception e) { return Stream.empty(); }
                    })
                    .toList();

                assertThat(violations)
                    .as("No static mutable fields in " + pkg + " package")
                    .isEmpty();
            }
        }
    }

    private Path resolveSourceDir(String packageName) {
        Path base = Path.of("src/main/java/com/arcadigitalis/backend", packageName);
        if (Files.exists(base)) return base;
        base = Path.of("backend/src/main/java/com/arcadigitalis/backend", packageName);
        return Files.exists(base) ? base : null;
    }
}
