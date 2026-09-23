package com.lenovo.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ReBatchSendRequest
{
    private Integer round; // 发送轮次
    private List<String> sequenceNumbers; // 选中的序列号列表
    private Boolean isALL;//全选：true。单选false
    private Map<String, Object> filters;
}