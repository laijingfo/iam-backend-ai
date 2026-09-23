package com.lenovo.strategy.impl;


import cn.hutool.core.collection.CollectionUtil;
import com.lenovo.bean.PreSendBean;
import com.lenovo.dto.BatchSendRequest;
import com.lenovo.mapper.FinalReviewExcelFileMapper;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.PreSendMailService;
import com.lenovo.service.impl.IEmailSendService;
import com.lenovo.strategy.CheckStrategy;
import com.lenovo.strategy.SendEmailStrategyType;
import com.lenovo.util.I18nUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Description TODO 给【最终审核结果是移除的用户】发送邮件
 * @ClassName UarSendFinalRemoveUserStrategy
 * @Author wangfenglong
 * @Date 2026/5/9 16:12
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class UarSendFinalRemoveUserStrategy implements CheckStrategy<Void,ResponseEntity<?>>
{
    private final IEmailSendService emailSendService;
    private final PreSendMailService preSendMailService;
    private final FinalReviewExcelFileMapper finalReviewExcelFileMapper;

    @Override
    public ResponseEntity<?> handle(BatchSendRequest request, Void param)
    {
        String sendFlag = "SendFinalRemoveUserEmail";
        String itCode = SecurityUtils.getCurrentUserId();
        List<PreSendBean> resultList = preSendMailService.getPreSendFinalRemoveUserList();
        if(CollectionUtil.isEmpty(resultList))
        {
            return ResponseEntity.ok(Map.of("success", false, "message", "pre_send_for_final_remove" + I18nUtil.get("send.email.error1")));
        }

        List<Map<String, Object>> list = finalReviewExcelFileMapper.selectAllExcel();
        if(CollectionUtil.isEmpty(list))
        {
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("send.email.error3")));
        }

        Map<String, byte[]> excelMap = new ConcurrentHashMap<>(list.size());
        for (Map<String, Object> row : list)
        {
            String excelItCode = (String) row.get("it_code");
            byte[] content = (byte[]) row.get("file_content");
            if (excelItCode != null && content != null)
            {
                excelMap.put(excelItCode, content);
            }
        }

        if(excelMap.isEmpty())
        {
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("send.email.error4")));
        }

        emailSendService.batchSendFinalRemoveUserEmailsAsync(excelMap,resultList,itCode,sendFlag);
        return ResponseEntity.ok(Map.of("success", true, "message", "[" + resultList.size() + "]" + I18nUtil.get("send.email.msg2")));
    }

    @Override
    public SendEmailStrategyType getType()
    {
        return SendEmailStrategyType.UAR_FINAL_REMOVE_USER_TYPE;
    }
}
