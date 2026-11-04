package com.alibaba.nacos.proto.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtoGeneratorIntegrationTest {

    private static final String TEST_GO_MODULE_BASE = "github.com/test-owner/nacos-sdk-proto/go";

    @TempDir
    Path tempDir;

    @Test
    void testGenerateProducesProtoFiles() throws Exception {
        Path outputDir = tempDir.resolve("proto");
        Path lockFile = tempDir.resolve("field-numbers.json");

        ProtoGenerator generator = new ProtoGenerator();
        generator.writer.setGoModuleBase(TEST_GO_MODULE_BASE);
        generator.generate(outputDir, lockFile, false);

        assertTrue(Files.exists(outputDir.resolve("common/common.proto")));
        assertTrue(Files.exists(outputDir.resolve("config/config_request.proto")));
        assertTrue(Files.exists(outputDir.resolve("config/config_response.proto")));
        assertTrue(Files.exists(outputDir.resolve("naming/naming_request.proto")));
        assertTrue(Files.exists(outputDir.resolve("naming/naming_response.proto")));

        assertTrue(Files.exists(lockFile));

        String configReq = Files.readString(outputDir.resolve("config/config_request.proto"));
        assertTrue(configReq.contains("message ConfigQueryRequest"));
        assertTrue(configReq.contains("string requestId = 1;"));
        assertTrue(configReq.contains("string dataId = 2;"));
        assertTrue(configReq.contains("string tag = 5;"));
        assertTrue(configReq.contains("package nacos.config;"));
        assertTrue(configReq.contains("option go_package = \"" + TEST_GO_MODULE_BASE + "/config\";"));

        String aiRequest = Files.readString(outputDir.resolve("ai/ai_request.proto"));
        assertEquals(3, countOccurrences(aiRequest,
                "// Deprecated: ignored by the Nacos server; use mcpName for resource identity.\n"
                        + "  string mcpId = 3 [deprecated = true];"));

        String aiResponse = Files.readString(outputDir.resolve("ai/ai_response.proto"));
        assertTrue(aiResponse.contains("string mcpId = 5;"));
        assertFalse(aiResponse.contains("string mcpId = 5 [deprecated = true];"));

        String mcpServerBasicInfo = Files.readString(outputDir.resolve("ai/mcpserverbasicinfo.proto"));
        assertTrue(mcpServerBasicInfo.contains("string id = 2;"));
        assertFalse(mcpServerBasicInfo.contains("string id = 2 [deprecated = true];"));
    }

    @Test
    void testGoModuleBaseRequired() {
        Path outputDir = tempDir.resolve("proto");
        Path lockFile = tempDir.resolve("field-numbers.json");

        ProtoGenerator generator = new ProtoGenerator();
        // goModuleBase not set — should fail
        assertThrows(IllegalStateException.class, () -> generator.generate(outputDir, lockFile, false));
    }

    @Test
    void testDryRunDoesNotWriteFiles() throws Exception {
        Path outputDir = tempDir.resolve("proto");
        Path lockFile = tempDir.resolve("field-numbers.json");

        ProtoGenerator generator = new ProtoGenerator();
        generator.generate(outputDir, lockFile, true);

        assertFalse(Files.exists(outputDir.resolve("config/config_request.proto")));
        assertFalse(Files.exists(lockFile));
    }

    @Test
    void testIdempotent() throws Exception {
        Path outputDir = tempDir.resolve("proto");
        Path lockFile = tempDir.resolve("field-numbers.json");

        ProtoGenerator generator = new ProtoGenerator();
        generator.writer.setGoModuleBase(TEST_GO_MODULE_BASE);
        generator.generate(outputDir, lockFile, false);
        String firstRun = Files.readString(outputDir.resolve("config/config_request.proto"));

        generator.generate(outputDir, lockFile, false);
        String secondRun = Files.readString(outputDir.resolve("config/config_request.proto"));

        assertEquals(firstRun, secondRun);
    }

    private int countOccurrences(String content, String target) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }
}
