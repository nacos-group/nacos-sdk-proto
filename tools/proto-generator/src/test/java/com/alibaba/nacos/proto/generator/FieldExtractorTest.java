package com.alibaba.nacos.proto.generator;

import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FieldExtractorTest {

    private final FieldExtractor extractor = new FieldExtractor();

    @Test
    void testFlattenConfigQueryRequest() {
        List<FieldInfo> fields = extractor.extract(ConfigQueryRequest.class);
        List<String> names = fields.stream().map(FieldInfo::name).toList();
        // requestId from Request, dataId/group/tenant from AbstractConfigRequest, tag/localMd5 from ConfigQueryRequest
        assertEquals(List.of("requestId", "dataId", "group", "tenant", "tag", "localMd5"), names);
    }

    @Test
    void testFlattenConfigQueryResponse() {
        List<FieldInfo> fields = extractor.extract(ConfigQueryResponse.class);
        List<String> names = fields.stream().map(FieldInfo::name).toList();
        // resultCode/errorCode/message/requestId from Response, then own fields
        assertTrue(names.contains("resultCode"));
        assertTrue(names.contains("requestId"));
        assertTrue(names.contains("content"));
        assertTrue(names.contains("lastModified"));
    }

    @Test
    void testExcludesHeaders() {
        List<FieldInfo> fields = extractor.extract(ConfigQueryRequest.class);
        List<String> names = fields.stream().map(FieldInfo::name).toList();
        assertFalse(names.contains("headers"));
    }

    @Test
    void testInstanceRequestHasNestedType() {
        List<FieldInfo> fields = extractor.extract(InstanceRequest.class);
        List<String> names = fields.stream().map(FieldInfo::name).toList();
        assertTrue(names.contains("instance"));
        FieldInfo instanceField = fields.stream()
            .filter(f -> f.name().equals("instance")).findFirst().orElseThrow();
        assertEquals("Instance", instanceField.type().getSimpleName());
    }
}
