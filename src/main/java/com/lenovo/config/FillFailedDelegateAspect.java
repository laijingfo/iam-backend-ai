package com.lenovo.config;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.entity.UarMailSendLog;
import com.lenovo.entity.UseAccessReviewEmailSend;
import com.lenovo.entity.UserAccessReview;
import com.lenovo.mapper.UarMailSendLogMapper;
import com.lenovo.mapper.UseAccessReviewEmailSendMapper;
import com.lenovo.security.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;


/**
 * @Description TODO 被授权的 BPO发送失败显示到失败列表
 * @ClassName FillFailedDelegateAspect
 * @Author wangfenglong
 * @Date 2026/6/8 10:57
 **/
@Aspect
@Component
@Order(2)
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
public class FillFailedDelegateAspect
{
    private final UarMailSendLogMapper uarMailSendLogMapper;
    private final UseAccessReviewEmailSendMapper useAccessReviewEmailSendMapper;

    @Around("execution(* com.lenovo.controller.UseAccessReviewStatusController.queryFailedRecords(..))")
    public Object around(ProceedingJoinPoint joinPoint )throws Throwable
    {
        Object originResult = joinPoint.proceed();
        if (!(originResult instanceof ResponseEntity<?>))
        {
            return originResult;
        }
        ResponseEntity<?> resp = (ResponseEntity<?>) originResult;
        Page<UserAccessReview> page = (Page<UserAccessReview>) resp.getBody();
        List<UserAccessReview> originRecords = page.getRecords();
        List<UserAccessReview> dataList = new ArrayList<>(originRecords);

        //空校验
        /*if (page == null || page.getRecords().isEmpty())
        {
            return originResult;
        }*/

        List<String> recipientTypes = List.of("BPO");
        QueryWrapper<UarMailSendLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("delegate","delegatee");
        queryWrapper.eq("status","FAILED");
        List<UarMailSendLog> delegateList =  uarMailSendLogMapper.selectList(queryWrapper);
        if(CollectionUtil.isNotEmpty(delegateList))
        {
            // key：去重后的邮箱，value：该邮箱对应的所有uuid集合
            Map<String, List<String>> mailMap = delegateList.stream()
            .filter(log -> log != null && StringUtils.isNotBlank(log.getRecipientEmails()))
            .collect(Collectors.groupingBy
            (
                log -> log.getRecipientEmails().trim(),
                Collectors.mapping(
                        UarMailSendLog::getUuid,
                        Collectors.toCollection(ArrayList::new)
                )
            ));

            mailMap.forEach((email, uuidList) ->
            {
                UserAccessReview  userAccessReview = new UserAccessReview();
                userAccessReview.setBpoEmail(email);
                userAccessReview.setOverallSendStatus("Failed");

                LambdaQueryWrapper<UarMailSendLog> queryWrapper3 = new LambdaQueryWrapper<>();
                queryWrapper3.eq(UarMailSendLog::getRecipientEmails, email);
                queryWrapper3.eq(UarMailSendLog::getStatus, "FAILED");
                queryWrapper3.eq(UarMailSendLog::getDelegate, "delegatee");
                List<UarMailSendLog> sequenceList = uarMailSendLogMapper.selectList(queryWrapper3);
                if(CollectionUtil.isNotEmpty(sequenceList))
                {
                    userAccessReview.setSequenceNumber(sequenceList.get(0).getRandomSequence());
                }

                LambdaQueryWrapper<UseAccessReviewEmailSend> queryWrapper2 = new LambdaQueryWrapper<>();
                if (CollectionUtil.isNotEmpty(uuidList))
                {
                    queryWrapper2.in(UseAccessReviewEmailSend::getReviewUuid, uuidList);
                }
                queryWrapper2.eq(UseAccessReviewEmailSend::getRecipientType, "BPO");
                List<UseAccessReviewEmailSend> allEmailSendList = useAccessReviewEmailSendMapper.selectList(queryWrapper2);
                userAccessReview.setBpoEmailStatus(CollectionUtil.isNotEmpty(allEmailSendList) ? allEmailSendList : null);
                dataList.add(userAccessReview);
            });
            page.setRecords(dataList);
            page.setTotal(dataList.size());

            // ========== 分页截断核心逻辑（适配 page页码、size每页条数）==========
            /*long currentPage = page.getCurrent(); // 前端传入的page=3
            long pageSize = page.getSize();       // 前端传入的size=10
            // 计算截取起止下标
            int startIndex = (int) ((currentPage - 1) * pageSize);
            int endIndex = Math.min(startIndex + (int) pageSize, dataList.size());
            List<UserAccessReview> currentPageData;
            if (startIndex >= dataList.size())
            {
                // 当前页码超出总数据，返回空列表
                currentPageData = new ArrayList<>();
            }
            else
            {
                // 截取当前页数据
                currentPageData = new ArrayList<>(dataList.subList(startIndex, endIndex));
            }

            //替换分页对象数据与总条数
            page.setRecords(currentPageData);
            page.setTotal(dataList.size());
            // 同步修正pages总页数（可选，前端部分组件依赖）
            long totalPages = dataList.size() % pageSize == 0 ? dataList.size() / pageSize : dataList.size() / pageSize + 1;
            page.setPages(totalPages);*/
        }
        return originResult;
    }
}
