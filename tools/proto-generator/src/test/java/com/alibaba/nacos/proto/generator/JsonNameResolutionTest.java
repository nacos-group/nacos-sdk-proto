package com.alibaba.nacos.proto.generator;

import com.alibaba.nacos.proto.generator.fixtures.FakeAnnotatedPojo;
import com.alibaba.nacos.proto.generator.fixtures.FakeBoxedFlagsPojo;
import com.alibaba.nacos.proto.generator.fixtures.FakeFuzzyWatchPojo;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonNameResolutionTest {

    private final FieldExtractor extractor = new FieldExtractor();

    private Map<String, String> jsonNamesOf(Class<?> clazz) {
        return extractor.extract(clazz).stream()
            .collect(Collectors.toMap(FieldInfo::name, FieldInfo::jsonName));
    }

    @Test
    void testPrimitiveBooleanIsGetterStripsPrefix() {
        Map<String, String> names = jsonNamesOf(FakeFuzzyWatchPojo.class);
        // Jackson serializes `boolean isInitializing` + is-getter as "initializing"
        assertEquals("initializing", names.get("isInitializing"));
        assertEquals("pattern", names.get("pattern"));
    }

    @Test
    void testBoxedBooleanWithGetIsGetterKeepsName() {
        Map<String, String> names = jsonNamesOf(FakeBoxedFlagsPojo.class);
        // `Boolean getIsRequired()` -> Jackson property stays "isRequired"
        assertEquals("isRequired", names.get("isRequired"));
        assertEquals("isSecret", names.get("isSecret"));
    }

    @Test
    void testJsonPropertyAnnotationWins() {
        Map<String, String> names = jsonNamesOf(FakeAnnotatedPojo.class);
        assertEquals("renamed_value", names.get("originalValue"));
    }
}
