package com.lenovo.config;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * @Description TODO 邮件发送上下文实体类
 * @ClassName EmailSendContextDTO
 * @Author wangfenglong
 * @Date 2025/12/22 09:41
 **/
@Data
public class EmailSendContextDTO
{
    /**
     * 批量编号
     */
    private String batchNo;

    /**
     * 发送轮次
     */
    private Integer round;

    /**
     * 批量请求的sequenceNumbers（用于更新状态）
     */
    private List<String> sequenceNumbers;

    // 空值处理：返回空列表而非null
    public List<String> getSequenceNumbers()
    {
        return sequenceNumbers == null ? Collections.emptyList() : sequenceNumbers;
    }
}
