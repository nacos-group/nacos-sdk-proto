package com.alibaba.nacos.api.ai.fixturegen;

import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.remote.response.Response;

public class FakeAgentSearchResponse extends Response {

    private Page<McpTool> page;

    public Page<McpTool> getPage() {
        return page;
    }

    public void setPage(Page<McpTool> page) {
        this.page = page;
    }
}
