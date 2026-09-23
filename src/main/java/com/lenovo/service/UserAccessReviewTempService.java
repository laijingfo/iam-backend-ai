package com.lenovo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lenovo.bean.AdvancedSearchBean;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.dto.SendCheckResponse;
import com.lenovo.entity.UserAccessReview;
import com.lenovo.entity.UserAccessReviewTemp;

import java.util.List;
import java.util.Map;

public interface UserAccessReviewTempService extends IService<UserAccessReviewTemp>
{
    int getSendEmailTotalCount(String roleFlag,String overallSendStatus,UseAccessReviewBean  bean);

    // 修改方法：根据sequenceNumber批量查询记录（不包含邮件状态）
    //List<Integer> getBySequenceNumbers(List<String> sequenceNumbers,String sendFlag);

    // 待发送页面单选邮件的查询
    //List<UserAccessReview>  getBySequenceNumbersBySendBatch(List<String> sequenceNumbers);

    // 修改方法：邮件发送前检查（支持复杂查询条件）
    SendCheckResponse checkEmailSend(Integer round, Boolean isALL, List<String> sequenceNumbers, Map<String, Object> filters,String sendFlag);

    // 新增方法：根据过滤条件查询记录
    //List<Integer> getByFilters(Map<String, Object> filters,String sendFlag);

    // 新增方法：刷新数据（重置发送状态并重新填充临时表）
    boolean refreshData();

    //List<UserAccessReview> getAllNeeDToSendEmailFromUserAccessReview(UseAccessReviewBean bean,String roleFlag);
}