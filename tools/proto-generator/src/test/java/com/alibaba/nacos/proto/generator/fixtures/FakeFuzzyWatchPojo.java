package com.alibaba.nacos.proto.generator.fixtures;

public class FakeFuzzyWatchPojo {

    private boolean isInitializing;

    private String pattern;

    public boolean isInitializing() {
        return isInitializing;
    }

    public void setInitializing(boolean initializing) {
        this.isInitializing = initializing;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
}
