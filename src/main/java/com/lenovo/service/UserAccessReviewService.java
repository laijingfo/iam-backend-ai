package com.lenovo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lenovo.bean.AdvancedSearchBean;
import com.lenovo.bean.EmailFailedBean;
import com.lenovo.bean.ImportResult;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.dto.BatchSendRequest;
import com.lenovo.dto.SendCheckResponse;
import com.lenovo.entity.UserAccessReview;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface UserAccessReviewService extends IService<UserAccessReview>
{
    // 新增方法：批量更新邮件发送状态
    boolean updateOverallSendStatusBatch(List<String> sequenceNumbers, String status,List<String> sequenceNumberList);
    boolean updateCurrentAroundBatch(List<String> sequenceNumbers,UseAccessReviewBean useAccessReviewBean);
    // 新增方法：根据发送状态分页查询
    Page<UserAccessReview> queryBySendStatus(UseAccessReviewBean  useAccessReviewBean,String overallSendStatus, Integer page, Integer size,String roleFlag);
    
    // 新增方法：查询历史记录（overall_send_status不为Pending）
    Page<UserAccessReview> queryHistoryRecords(Integer page, Integer size, UseAccessReviewBean useAccessReviewBean);
    // 新增方法：根据发送状态获取去重的应用名称列表
    List<AdvancedSearchBean> getDistinctApplicationBySendStatus(String roleFlag,String overallSendStatus,UseAccessReviewBean useAccessReviewBean);

    // 新增方法：获取当前用户角色
    public EmailFailedBean getCurrentUserRole();

    // 新增方法：取当前登陆账户选择的过滤条件
    public UseAccessReviewBean getCurrentRequestFilters(BatchSendRequest request);

    // 新增方法：根据筛选条件获取选中的数据
    public List<UserAccessReview> getSelectedRecords(BatchSendRequest request,String successFailedFlag);

    /**
     * 获取历史记录（overall_send_status不为Pending的记录）
     */
    List<UserAccessReview> getAllHistoryRecords(UseAccessReviewBean useAccessReviewBean, String language);

    //发送邮件统计各种发送状态的邮件数量
    int getSendEmailTotalCount(String roleFlag,String overallSendStatus,UseAccessReviewBean  bean);

    // 新增方法：更新待发送列表
    boolean refreshData();

    // 修改方法：(待发送页面和发送失败页面)邮件发送前检查（支持复杂查询条件）
    SendCheckResponse checkEmailSend(Integer round, Boolean isALL, List<String> sequenceNumbers, Map<String, Object> filters, String sendFlag);


    boolean localMergeUarDoUpdate(String cmdbId);

    ImportResult importUarData(MultipartFile file, String cmdbId) throws IOException;

    List<String> getDistinctSystemRolesByCmdbId(String cmdbId, String bpo);

    Map<String, String> relatedLeaderAndSave(List<String> employees);

    ImportResult importUARExceptionProcessData(MultipartFile file, List<String> dataRange) throws IOException;
}