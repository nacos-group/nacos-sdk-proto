package com.alibaba.nacos.proto.generator.fixtures;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.remote.response.Response;

public class FakeSearchResponse extends Response {

    private Page<FakeCatalogEntry> page;

    public Page<FakeCatalogEntry> getPage() {
        return page;
    }

    public void setPage(Page<FakeCatalogEntry> page) {
        this.page = page;
    }
}
