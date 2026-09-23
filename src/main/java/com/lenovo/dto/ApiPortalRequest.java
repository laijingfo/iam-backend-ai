package com.lenovo.dto;

import lombok.Data;

@Data
public class ApiPortalRequest {
    private String apiName;
    private Long pageIndex;
    private Long pageSize;

    public ApiPortalRequest() {
        this.pageIndex = 1L;
        this.pageSize = 1000L;
    }
}
