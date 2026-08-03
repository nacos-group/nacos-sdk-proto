package com.alibaba.nacos.proto.generator.fixtures;

import com.alibaba.nacos.api.remote.response.Response;

import java.util.Date;

public class FakeUnmappableResponse extends Response {

    private Date timestamp;

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
}
