package com.lenovo.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class SendCheckRequest {
    private Integer round; // 发送轮次
    private Boolean isALL; // 是否全选
    private List<String> sequenceNumbers; // 选中的序列号列表
    private Map<String, Object> filters; // 过滤条件

    public SendCheckRequest() {}

    public SendCheckRequest(Integer round, Boolean isALL, List<String> sequenceNumbers, Map<String, Object> filters) {
        this.round = round;
        this.isALL = isALL;
        this.sequenceNumbers = sequenceNumbers;
        this.filters = filters;
    }
}