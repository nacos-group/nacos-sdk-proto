package com.alibaba.nacos.proto.generator;

import com.alibaba.nacos.proto.generator.fixtures.FakeBoxedFlagsPojo;
import com.alibaba.nacos.proto.generator.fixtures.FakeFuzzyWatchPojo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonNameSupportTest {

    private static final String TEST_GO_MODULE_BASE = "github.com/test-owner/nacos-sdk-proto/go";

    @TempDir
    Path tempDir;

    private Path generate(Class<?>... roots) throws Exception {
        Path outputDir = tempDir.resolve("proto");
        Path lockFile = tempDir.resolve("field-numbers.json");
        ProtoGenerator generator = new ProtoGenerator();
        generator.writer.setGoModuleBase(TEST_GO_MODULE_BASE);
        generator.generateForClasses(List.of(roots), outputDir, lockFile, false);
        return outputDir;
    }

    @Test
    void testBooleanIsFieldEmitsJsonNameOption() throws Exception {
        Path outputDir = generate(FakeFuzzyWatchPojo.class);
        String common = Files.readString(outputDir.resolve("common/common.proto"));
        assertTrue(common.contains("bool isInitializing = 1 [json_name = \"initializing\"];"), common);
        // Fields whose wire name already matches must stay bare
        assertTrue(common.contains("string pattern = 2;"), common);
    }

    @Test
    void testBoxedBooleanGetIsGetterStaysBare() throws Exception {
        Path outputDir = generate(FakeBoxedFlagsPojo.class);
        String common = Files.readString(outputDir.resolve("common/common.proto"));
        assertTrue(common.contains("bool isRequired = 1;"), common);
        assertTrue(common.contains("bool isSecret = 2;"), common);
        assertFalse(common.contains("json_name"), common);
    }
}
