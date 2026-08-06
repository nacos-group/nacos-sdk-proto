package com.alibaba.nacos.proto.generator.fixtures;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.remote.response.Response;

public class FakeSecondResponse extends Response {

    private Page<FakeCatalogEntry> result;

    public Page<FakeCatalogEntry> getResult() {
        return result;
    }

    public void setResult(Page<FakeCatalogEntry> result) {
        this.result = result;
    }
}
