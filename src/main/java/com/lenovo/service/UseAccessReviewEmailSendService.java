package com.lenovo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lenovo.entity.UseAccessReviewEmailSend;

import java.util.List;
import java.util.Map;

public interface UseAccessReviewEmailSendService extends IService<UseAccessReviewEmailSend> {

    List<UseAccessReviewEmailSend> getByReviewUuidAndRecipientType(String uuid,String recipientType);
    //批量查询指定uuid列表和收件人类型的邮件发送记录（解决N+1查询）
    Map<String, List<UseAccessReviewEmailSend>> getBatchByUuidListAndRecipientTypes(List<String> uuidList, List<String> recipientTypes);

    // 新增方法：根据UUID、轮次和收件人类型查询发送记录
    List<UseAccessReviewEmailSend> getByReviewUuidAndRoundAndType(String reviewUuid, Integer round, String recipientType);
}