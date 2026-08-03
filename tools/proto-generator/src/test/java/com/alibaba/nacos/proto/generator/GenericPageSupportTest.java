package com.alibaba.nacos.proto.generator;

import com.alibaba.nacos.api.ai.fixturegen.FakeAgentSearchResponse;
import com.alibaba.nacos.proto.generator.fixtures.FakeSearchResponse;
import com.alibaba.nacos.proto.generator.fixtures.FakeSecondResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GenericPageSupportTest {

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
    void testPageFieldMonomorphizedIntoConcreteMessage() throws Exception {
        Path outputDir = generate(FakeSearchResponse.class);
        String common = Files.readString(outputDir.resolve("common/common.proto"));

        // Page<FakeCatalogEntry> becomes a concrete message mirroring Page's JSON shape
        assertTrue(common.contains("message FakeCatalogEntryPage {"), common);
        assertTrue(common.contains("int32 totalCount"), common);
        assertTrue(common.contains("int32 pageNumber"), common);
        assertTrue(common.contains("int32 pagesAvailable"), common);
        assertTrue(common.contains("repeated FakeCatalogEntry pageItems"), common);

        // The response field references the concrete message
        assertTrue(common.contains("FakeCatalogEntryPage page"), common);

        // The element type is discovered as a normal domain object
        assertTrue(common.contains("message FakeCatalogEntry {"), common);

        // No raw, undefined "Page" leaks into the proto
        assertFalse(common.contains("message Page {"), common);
        assertFalse(common.contains(" Page page"), common);
    }

    @Test
    void testInstantiationPlacedInElementModuleWithImports() throws Exception {
        Path outputDir = generate(FakeAgentSearchResponse.class);

        String response = Files.readString(outputDir.resolve("ai/ai_response.proto"));
        assertTrue(response.contains("McpToolPage page"), response);
        assertTrue(response.contains("import \"ai/mcptoolpage.proto\";"), response);

        String pageProto = Files.readString(outputDir.resolve("ai/mcptoolpage.proto"));
        assertTrue(pageProto.contains("message McpToolPage {"), pageProto);
        assertTrue(pageProto.contains("repeated McpTool pageItems"), pageProto);
        assertTrue(pageProto.contains("import \"ai/mcptool.proto\";"), pageProto);
        assertTrue(pageProto.contains("package nacos.ai;"), pageProto);
    }

    @Test
    void testUnmappedTypeFailsFastInsteadOfEmittingUndefinedProtoType() {
        // java.* types are skipped by domain discovery, so no message exists for them;
        // the generator must fail loudly instead of writing a proto that protoc rejects later
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> generate(com.alibaba.nacos.proto.generator.fixtures.FakeUnmappableResponse.class));
        assertTrue(ex.getMessage().contains("java.util.Date"), ex.getMessage());
    }

    @Test
    void testSamePageInstantiationGeneratedOnlyOnce() throws Exception {
        Path outputDir = generate(FakeSearchResponse.class, FakeSecondResponse.class);
        String common = Files.readString(outputDir.resolve("common/common.proto"));
        int first = common.indexOf("message FakeCatalogEntryPage {");
        int last = common.lastIndexOf("message FakeCatalogEntryPage {");
        assertTrue(first >= 0, common);
        assertEquals(first, last, "Page instantiation must be generated exactly once");
    }
}
