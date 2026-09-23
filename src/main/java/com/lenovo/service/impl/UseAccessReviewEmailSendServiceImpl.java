package com.lenovo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.entity.UseAccessReviewEmailSend;
import com.lenovo.mapper.UseAccessReviewEmailSendMapper;
import com.lenovo.service.UseAccessReviewEmailSendService;
import org.springframework.util.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UseAccessReviewEmailSendServiceImpl extends ServiceImpl<UseAccessReviewEmailSendMapper, UseAccessReviewEmailSend> implements UseAccessReviewEmailSendService {

    @Override
    public List<UseAccessReviewEmailSend> getByReviewUuidAndRecipientType(String uuid, String recipientType)
    {
        LambdaQueryWrapper<UseAccessReviewEmailSend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UseAccessReviewEmailSend::getReviewUuid, uuid)
                .eq(UseAccessReviewEmailSend::getRecipientType, recipientType)
                .orderByAsc(UseAccessReviewEmailSend::getSendRound);
        return list(queryWrapper);
    }

    /**
     * @Description TODO 批量查询指定uuid列表和收件人类型的邮件发送记录（解决N+1查询）
     * @param uuidList 审核记录uuid列表
     * @param recipientTypes 收件人类型列表
     * @return Map<"uuid-收件人类型", List<UseAccessReviewEmailSend>> 内存映射，便于快速获取
     * @author wangfenglong
     * @date 2026/1/4 11:20
    **/
    @Override
    public Map<String, List<UseAccessReviewEmailSend>> getBatchByUuidListAndRecipientTypes(List<String> uuidList, List<String> recipientTypes)
    {
        // 1. 空值校验
        if (CollectionUtils.isEmpty(uuidList) || CollectionUtils.isEmpty(recipientTypes))
        {
            return new HashMap<>();
        }

        // 2. 构建批量查询条件（复用LambdaQueryWrapper，仅1次SQL执行）
        LambdaQueryWrapper<UseAccessReviewEmailSend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(UseAccessReviewEmailSend::getReviewUuid, uuidList) // 批量匹配uuid
                .in(UseAccessReviewEmailSend::getRecipientType, recipientTypes) // 批量匹配收件人类型
                .orderByAsc(UseAccessReviewEmailSend::getSendRound); // 仅1次排序，避免重复开销

        // 3. 执行批量查询（仅1次数据库交互）
        List<UseAccessReviewEmailSend> allEmailSendList = list(queryWrapper);

        // 4. 转换为内存Map，便于快速取值（核心：将列表转为KV结构，O(1)查询效率）
        Map<String, List<UseAccessReviewEmailSend>> emailSendMap = new HashMap<>();
        for (UseAccessReviewEmailSend emailSend : allEmailSendList)
        {
            // 拼接Key：uuid-收件人类型（确保唯一性）
            String mapKey = emailSend.getReviewUuid() + "-" + emailSend.getRecipientType();
            // 按Key分组，收集对应邮件列表（computeIfAbsent：Key不存在时创建空列表）
            emailSendMap.computeIfAbsent(mapKey, k -> new ArrayList<>()).add(emailSend);
        }
        return emailSendMap;
    }


    @Override
    public List<UseAccessReviewEmailSend> getByReviewUuidAndRoundAndType(String reviewUuid, Integer round, String recipientType)
    {
        try
        {
            LambdaQueryWrapper<UseAccessReviewEmailSend> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(UseAccessReviewEmailSend::getReviewUuid, reviewUuid)
                    //.eq(UseAccessReviewEmailSend::getSendRound, round)
                    .eq(UseAccessReviewEmailSend::getRecipientType, recipientType)
                    .orderByDesc(UseAccessReviewEmailSend::getSendTime);
            return list(queryWrapper);
        }
        catch (Exception e)
        {
            log.error("查询邮件发送记录失败: reviewUuid={}, round={}, type={}");
            throw new RuntimeException("查询邮件发送记录失败: " + e.getMessage());
        }
    }
}
