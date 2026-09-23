package com.lenovo.config;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import org.springframework.util.Assert;

/**
 * @Description TODO 邮件发送的ThreadLocal上下文
 * @ClassName EmailSendContext
 * @Author wangfenglong
 * @Date 2025/12/13 23:09
 **/
@Component
public class EmailSendContext
{

    // 替换为InheritableThreadLocal，支持父子线程传递
    private static final ThreadLocal<EmailSendContextDTO> CONTEXT = new InheritableThreadLocal<>();



    // ========== 批量设置上下文（减少多次set调用） ==========
    /**
     * 初始化上下文（一次性设置所有属性）
     * @param batchNo 批量编号
     * @param round 发送轮次
     * @param sequenceNumbers 序列号列表
     */
    public static void init(String batchNo, Integer round, List<String> sequenceNumbers)
    {
        // 空值校验：核心属性不能为空
        Assert.hasText(batchNo, "batchNo不能为空");
        Assert.notNull(round, "round不能为空");
        EmailSendContextDTO dto = new EmailSendContextDTO();
        dto.setBatchNo(batchNo);
        dto.setRound(round);
        dto.setSequenceNumbers(sequenceNumbers);
        CONTEXT.set(dto);
    }

    // ========== 单独设置属性 ==========
    public static void setBatchNo(String batchNo)
    {
        getContextOrCreate().setBatchNo(batchNo);
    }

    public static void setRound(Integer round)
    {
        getContextOrCreate().setRound(round);
    }

    public static void setSequenceNumbers(List<String> sequenceNumbers)
    {
        getContextOrCreate().setSequenceNumbers(sequenceNumbers);
    }

    // ========== 获取属性（添加空值保护） ==========
    public static String getBatchNo()
    {
        return getContextOrNull() != null ? getContextOrNull().getBatchNo() : null;
    }

    public static Integer getRound()
    {
        return getContextOrNull() != null ? getContextOrNull().getRound() : null;
    }

    public static List<String> getSequenceNumbers()
    {
        return getContextOrNull() != null ? getContextOrNull().getSequenceNumbers() : Collections.emptyList();
    }

    // ========== 内部辅助方法：避免空指针 ==========
    /**
     * 获取上下文，若不存在则返回null
     */
    public static EmailSendContextDTO getContextOrNull()
    {
        return CONTEXT.get();
    }

    /**
     * 获取上下文，若不存在则创建空的DTO（用于单独set属性的场景）
     */
    private static EmailSendContextDTO getContextOrCreate()
    {
        EmailSendContextDTO dto = CONTEXT.get();
        if (dto == null) {
            dto = new EmailSendContextDTO();
            CONTEXT.set(dto);
        }
        return dto;
    }

    // ========== 清除上下文（必须手动调用） ==========
    public static void clear()
    {
        CONTEXT.remove();
    }
}
