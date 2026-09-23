package com.lenovo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

/**
 * @Description TODO 通知管理--发送邮件时前端传过来的参数对象
 * @author wangfenglong
 * @date 2025/11/28 17:09
**/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchSendRequest
{
    private List<String> sequenceNumbers;//单选：前端传sequenceNumbers。全选：前端不传sequenceNumbers
    private Integer round;
    private Boolean isALL;//全选：true。单选false
    private Map<String, Object> filters; // 过滤条件
}