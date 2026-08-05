package com.alibaba.nacos.proto.generator.fixtures;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FakeAnnotatedPojo {

    @JsonProperty("renamed_value")
    private String originalValue;

    public String getOriginalValue() {
        return originalValue;
    }

    public void setOriginalValue(String originalValue) {
        this.originalValue = originalValue;
    }
}
