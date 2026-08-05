package com.alibaba.nacos.proto.generator;

import java.lang.reflect.Type;

public record FieldInfo(String name, Class<?> type, Type genericType, String jsonName) {

    public FieldInfo(String name, Class<?> type, Type genericType) {
        this(name, type, genericType, name);
    }

    public boolean hasCustomJsonName() {
        return !name.equals(jsonName);
    }
}
