package com.alibaba.nacos.proto.generator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that Java Payload class fields match their generated proto message fields.
 */
class FieldConsistencyTest {

    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "^\\s+.+\\s+(\\w+)\\s*=\\s*\\d+\\s*(?:\\[.*\\])?;");
    private static final Pattern MESSAGE_START = Pattern.compile(
            "^message\\s+(\\w+)\\s*\\{");

    private static Path protoOutputDir;
    private static Map<Class<?>, String> classToMessageName;

    @BeforeAll
    static void generateProto(@TempDir Path tempDir) throws IOException {
        protoOutputDir = tempDir;
        Path lockFile = tempDir.resolve("field-numbers.json");
        Files.writeString(lockFile, "{}");

        ProtoGenerator generator = new ProtoGenerator();
        generator.writer.setGoModuleBase("github.com/test/nacos-sdk-proto/go");
        generator.generate(protoOutputDir, lockFile, false);
        classToMessageName = generator.getClassToMessageName();
    }

    @Test
    void allPayloadFieldsShouldMatchProtoMessage() throws IOException {
        FieldExtractor extractor = new FieldExtractor();
        List<String> mismatches = new ArrayList<>();

        for (Map.Entry<Class<?>, String> entry : classToMessageName.entrySet()) {
            Class<?> clazz = entry.getKey();
            String messageName = entry.getValue();

            Set<String> javaFields = new LinkedHashSet<>();
            for (FieldInfo fi : extractor.extract(clazz)) {
                javaFields.add(fi.name());
            }

            Set<String> protoFields = parseProtoFields(protoOutputDir, messageName);

            if (protoFields == null) {
                mismatches.add(String.format("Message '%s' (class %s) not found in proto files",
                        messageName, clazz.getSimpleName()));
                continue;
            }

            Set<String> javaOnly = new TreeSet<>(javaFields);
            javaOnly.removeAll(protoFields);
            Set<String> protoOnly = new TreeSet<>(protoFields);
            protoOnly.removeAll(javaFields);

            if (!javaOnly.isEmpty() || !protoOnly.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Mismatch for %s (%s):", messageName, clazz.getSimpleName()));
                if (!javaOnly.isEmpty()) {
                    sb.append(" Java-only=").append(javaOnly);
                }
                if (!protoOnly.isEmpty()) {
                    sb.append(" Proto-only=").append(protoOnly);
                }
                mismatches.add(sb.toString());
            }
        }

        if (!mismatches.isEmpty()) {
            fail("Field consistency violations:\n" + String.join("\n", mismatches));
        }
    }

    private Set<String> parseProtoFields(Path outputDir, String messageName) throws IOException {
        try (Stream<Path> paths = Files.walk(outputDir)) {
            List<Path> protoFiles = paths
                    .filter(p -> p.toString().endsWith(".proto"))
                    .toList();

            for (Path protoFile : protoFiles) {
                List<String> lines = Files.readAllLines(protoFile);
                Set<String> fields = extractFieldsFromMessage(lines, messageName);
                if (fields != null) {
                    return fields;
                }
            }
        }
        return null;
    }

    private Set<String> extractFieldsFromMessage(List<String> lines, String messageName) {
        boolean inMessage = false;
        int braceDepth = 0;
        Set<String> fields = new LinkedHashSet<>();

        for (String line : lines) {
            if (!inMessage) {
                Matcher msgMatcher = MESSAGE_START.matcher(line);
                if (msgMatcher.find() && msgMatcher.group(1).equals(messageName)) {
                    inMessage = true;
                    braceDepth = 1;
                }
            } else {
                for (char c : line.toCharArray()) {
                    if (c == '{') braceDepth++;
                    else if (c == '}') braceDepth--;
                }
                if (braceDepth <= 0) {
                    return fields;
                }
                Matcher fieldMatcher = FIELD_PATTERN.matcher(line);
                if (fieldMatcher.find()) {
                    fields.add(fieldMatcher.group(1));
                }
            }
        }
        return inMessage ? fields : null;
    }
}
