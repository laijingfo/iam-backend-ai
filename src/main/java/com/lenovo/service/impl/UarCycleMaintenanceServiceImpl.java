package com.lenovo.service.impl;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.bean.*;
import com.lenovo.config.GlobalBusinessStatusEnum;
import com.lenovo.dto.BatchSendRequest;
import com.lenovo.dto.BatchSendResult;
import com.lenovo.entity.*;
import com.lenovo.mapper.ItsApplicationDataMapper;
import com.lenovo.mapper.UarCycleMaintenanceMapper;
import com.lenovo.mapper.UarCycleSettingMapper;
import com.lenovo.mapper.UserAccessReviewMapper;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.SendEmailActionLogService;
import com.lenovo.service.UarCycleMaintenanceService;
import com.lenovo.service.UarMailService;
import com.lenovo.service.UarMailTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class UarCycleMaintenanceServiceImpl extends ServiceImpl<UarCycleMaintenanceMapper, UarCycleMaintenance> implements UarCycleMaintenanceService {

    @Resource
    private UserAccessReviewMapper userAccessReviewMapper;

    @Resource
    private ItsApplicationDataMapper itsApplicationDataMapper;

    @Resource
    private UarMailTemplateService uarMailTemplateService;

    @Resource
    private UarMailService uarMailService;

    @Resource
    private SendEmailActionLogService sendEmailActionLogService;

    @Resource
    private UarCycleSettingMapper uarCycleSettingMapper;

    @Resource(name = "emailSendExecutor")
    private AsyncTaskExecutor emailSendExecutorSchedule;

    private static final SimpleDateFormat formatterForUser = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    public Page<ApplicationCycleInfoBean> query(UarCycleMaintenanceBean uarCycleMaintenanceBean) {
        Page p = new Page(uarCycleMaintenanceBean.getPage(), uarCycleMaintenanceBean.getSize());
        return getBaseMapper().query(p, uarCycleMaintenanceBean);
    }

    @Override
    public List<ApplicationCycleInfoBean> queryFullData(UarCycleMaintenanceBean uarCycleMaintenanceBean) {
        return getBaseMapper().queryFullData(uarCycleMaintenanceBean);
    }

    /**
     * 批量更新[应用]的上线时间
     * @param
     * @return
     */
    @Override
    @Transactional
    public Integer batchUpdateAppOnlineTime(UarCycleBean uarCycleBean) {
        // 如果是全选的话，则查询出所有符合条件的cmdbId
        if (uarCycleBean.getIsALL()) {
            List<String> cmdbIdList = getBaseMapper().queryIdListByFilters(uarCycleBean.getFilters());
            uarCycleBean.setCmdbId(cmdbIdList);
        }
        if (uarCycleBean.getCmdbId() == null || uarCycleBean.getCmdbId().isEmpty()) {
            return 0;
        }

        return itsApplicationDataMapper.batchUpdateAppOnlineTime(uarCycleBean);
    }

    @Override
    public Integer batchUpdateAppOfflineTime(UarCycleBean uarCycleBean) {
        // 如果是全选的话，则查询出所有符合条件的cmdbId
        if (uarCycleBean.getIsALL()) {
            List<String> cmdbIdList = getBaseMapper().queryIdListByFilters(uarCycleBean.getFilters());
            uarCycleBean.setCmdbId(cmdbIdList);
        }
        if (uarCycleBean.getCmdbId() == null || uarCycleBean.getCmdbId().isEmpty()) {
            return 0;
        }
        return itsApplicationDataMapper.batchUpdateEndMonth(uarCycleBean);
    }

    /**
     * (批量上线,批量下线,批量开启周期,批量关闭周期)发送邮件
     * @author wangfenglong
     * @date 2026/5/26 15:32
     */
    @Override
    public CompletableFuture<List<ItsApplicationData>> batchSendEmailsAsyncByOnlineTimeAndOfflineTime(UarCycleBean uarCycleBean, String itCode,String sendFlag,String batchNo)
    {

        String tagModel;
        int batchSize = 50;
        Set<UarCycleMaintnanceBean> recipients = new HashSet<>(Set.of());
        String toSomeone = "ToUarProcessor";
        switch (sendFlag)
        {
            case "AppOnline": tagModel = "1"; break; //上线
            case "AppOffline": tagModel = "2"; break; //下线
            case "InitUarCycle": tagModel = "3"; break; //开启UAR周期预发邮件
            case "CompleteUarCycle": tagModel = "4"; break; //关闭UAR周期预发邮件
            default: tagModel = "1"; break;
        }

        UarMailTemplate uarTemplate = uarMailTemplateService.findTemplateWithMaxVersion(tagModel, toSomeone);
        if(uarTemplate == null)
        {
            String errorMsg = "UAR周期设置：发送邮件：未找到对应模板: sendFlag=" + sendFlag + ",tag=" +tagModel + ",toSomeone=" + toSomeone;
            log.error(GlobalBusinessStatusEnum.COLOR_GREEN.desc + errorMsg + GlobalBusinessStatusEnum.COLOR_RESET.desc);
            return CompletableFuture.failedFuture(new RuntimeException(errorMsg));
        }

        List<ItsApplicationData> applicationData = itsApplicationDataMapper.getApplicationDataByCmdbIds(uarCycleBean);
        if(CollectionUtils.isEmpty(applicationData))
        {
            String infoMsg = "UAR周期设置：发送邮件：sendFlag= "+ sendFlag + ",applicationData是空,没有查询出需要发送的邮件信息";
            log.info(GlobalBusinessStatusEnum.COLOR_GREEN.desc + infoMsg + GlobalBusinessStatusEnum.COLOR_RESET.desc);
            return CompletableFuture.failedFuture(new RuntimeException(infoMsg));
        }

        // 这里的取值优先级：有 S&A 就发给 S&A ，没有S&A 就发给运维Owner
        List<String> allowSendEmailList = itsApplicationDataMapper.getSAndAEmailsByCmdbIds(uarCycleBean);

        if(CollectionUtils.isEmpty(allowSendEmailList))
        {
            String infoMsg = "UAR周期设置：发送邮件：sendFlag= "+ sendFlag + ",allowSendEmailList是空,没有查询出需要发送的邮件信息";
            log.info(GlobalBusinessStatusEnum.COLOR_GREEN.desc + infoMsg + GlobalBusinessStatusEnum.COLOR_RESET.desc);
            return CompletableFuture.failedFuture(new RuntimeException(infoMsg));
        }

        // 最终结果 map
        Map<String, List<ItsApplicationData>> userMap = new HashMap<>();
        for (String email : allowSendEmailList)
        {
            String[] emailParts = email.split("@");
            String u = emailParts[0];

            //找到当前用户相关的数据 + 根据 cmdbId 去重
            List<ItsApplicationData> appList = new ArrayList<>(applicationData.stream()
            .filter(data -> u.equals(data.getOperationOwner()) || u.equals(data.getOperationFocal()))
            .collect(Collectors.toMap
            (
                ItsApplicationData::getCmdbId, // key：cmdbId
                data ->    // value：新建对象，只存 cmdbId + applicationName
                {
                    ItsApplicationData app = new ItsApplicationData();
                    app.setCmdbId(data.getCmdbId());
                    app.setApplicationName(data.getApplicationName());
                    app.setDecommissionReason(data.getDecommissionReason());
                    return app;
                }, (oldValue, newValue) -> oldValue  // 重复 cmdbId 保留第一个
            )).values());
            userMap.put(email, appList);
        }

        for (Map.Entry<String, List<ItsApplicationData>> entry : userMap.entrySet())
        {
            UarCycleMaintnanceBean uarCycleMaintnanceBean = new UarCycleMaintnanceBean();
            uarCycleMaintnanceBean.setEmail(entry.getKey());

            // 获取 value：该用户对应的应用列表
            List<ItsApplicationData> appList = entry.getValue();
            Map<String, String> variablesMap = new HashMap<>();

            String cmdbIds = appList.stream().map(ItsApplicationData::getCmdbId).filter(Objects::nonNull).collect(Collectors.joining(","));
            String appName = appList.stream().map(ItsApplicationData::getApplicationName).filter(Objects::nonNull).collect(Collectors.joining(","));
            String decommissionReason = appList.stream().map(ItsApplicationData::getDecommissionReason).filter(Objects::nonNull).findFirst().orElse(null);

            variablesMap.put("{appId}", cmdbIds);
            variablesMap.put("{appName}", appName);
            variablesMap.put("{decommissionReason}", StringUtils.isEmpty(decommissionReason) ? "未知" : decommissionReason);
            //variables.put("{uarName}", uarName);
            variablesMap.put("{dueDate}", Objects.isNull(uarTemplate.getDueDate()) ? "dateTime" : formatterForUser.format(uarTemplate.getDueDate()));
            uarCycleMaintnanceBean.setVariables(variablesMap);
            recipients.add(uarCycleMaintnanceBean);
        }

        if(CollectionUtils.isEmpty(recipients))
        {
            String infoMsg = "UAR周期设置：发送邮件：sendFlag= "+ sendFlag + ",recipients是空,没有整理出需要发送的邮件信息";
            log.info(GlobalBusinessStatusEnum.COLOR_GREEN.desc + infoMsg + GlobalBusinessStatusEnum.COLOR_RESET.desc);
            return CompletableFuture.failedFuture(new RuntimeException(infoMsg));
        }
        
        List<UarCycleMaintnanceBean> recipientList = new ArrayList<>(recipients);
        int total = recipientList.size();

        try
        {
            List<CompletableFuture<Void>> batchTaskFutures = new ArrayList<>();
            for(int i = 0; i < total; i += batchSize)
            {
                int end = Math.min(i + batchSize, total);
                List<UarCycleMaintnanceBean> batchList = new ArrayList<>(recipientList.subList(i, end));
                List<CompletableFuture<Void>> singleBatchFutures = new ArrayList<>();
                for(final UarCycleMaintnanceBean record : batchList)
                {
                    //每次循环新建Map，避免共享修改（浅拷贝即可，因为value都是String）
                    Map<String, String> taskVariables = new HashMap<>(record.getVariables());
                    String itCodeValue = null;
                    try
                    {
                        // 防止邮箱格式错误导致数组越界
                        String[] emailParts = record.getEmail().split("@");
                        if (emailParts.length >= 2)
                        {
                            itCodeValue = emailParts[0];
                        }
                        else
                        {
                            log.error("UAR周期设置：邮箱格式错误，无法提取itCode，邮件：{}", record.getEmail());
                            SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
                            sendEmailActionLog.setItCode(itCode);
                            sendEmailActionLog.setOperation("UAR周期设置:通知邮件");
                            sendEmailActionLog.setSendFlag(sendFlag);
                            sendEmailActionLog.setMessage("UAR周期设置：邮箱格式错误，无法提取itCode，邮件：" + record);
                            sendEmailActionLog.setStackTrace("");
                            sendEmailActionLog.setBatchNo(batchNo);
                            sendEmailActionLog.setCreateDate(LocalDateTime.now());
                            sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
                            continue;
                        }
                    }
                    catch (Exception e)
                    {
                        log.error("UAR周期设置：提取itCode失败，邮件：{}", record, e);
                        continue;
                    }
                    // 赋值到当前任务专属的Map
                    taskVariables.put("{itCode}", itCodeValue);
                    final Map<String, String> finalTaskVariables = taskVariables;
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                    {
                        try
                        {
                            uarMailService.sendTemplatedEmailOnlyForLmBpoAndUARSetting(uarTemplate, Collections.singleton(record.getEmail()), null, finalTaskVariables, batchNo, sendFlag);
                        }
                        catch (Exception e)
                        {
                            log.error("UAR周期设置：发送邮件：执行单条邮件发送任务失败,发送标志sendFlag:{},邮件:{}",sendFlag, record, e);
                        }
                    }, emailSendExecutorSchedule);
                    singleBatchFutures.add(future);
                }
                CompletableFuture<Void> batchFuture = CompletableFuture.allOf(singleBatchFutures.toArray(new CompletableFuture<?>[]{}));
                int finalI = i;
                // 将异常处理后的future加入列表，确保批次级异常不会传播到超时控制层
                CompletableFuture<Void> handledBatchFuture = batchFuture.thenRun(() -> log.info("UAR周期设置：发送邮件：发送标标志sendFlag:{},邮件发送批次任务完成【批次号：{}】：{} - {} 条，共{}条 ", sendFlag,batchNo, finalI, end, total))
                .exceptionally(e ->
                {
                    log.error("UAR周期设置：发送邮件：发送标标志sendFlag:{},邮件发送批次任务执行异常【批次号：{}】：{} - {} 条", sendFlag,batchNo, finalI, end, e);
                    SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
                    sendEmailActionLog.setItCode(itCode);
                    sendEmailActionLog.setOperation("UAR周期设置:通知邮件");
                    sendEmailActionLog.setSendFlag(sendFlag);
                    sendEmailActionLog.setMessage(e.getMessage());
                    sendEmailActionLog.setStackTrace(Arrays.toString(e.getStackTrace()));
                    sendEmailActionLog.setBatchNo(batchNo);
                    sendEmailActionLog.setCreateDate(LocalDateTime.now());
                    sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
                    return null;
                });
                batchTaskFutures.add(handledBatchFuture);
            }

            //合并所有批次任务
            CompletableFuture<Void> allBatchFuture = CompletableFuture.allOf(batchTaskFutures.toArray(new CompletableFuture[0]));
            CompletableFuture<Void> timeoutFuture = allBatchFuture.orTimeout(120, TimeUnit.MINUTES)
            .exceptionally(e ->
            {
                log.error("UAR周期设置：发送邮件：发送标标志sendFlag:{},批量邮件发送任务超时，批次号={}", sendFlag,batchNo, e);
                for (Future<?> emailFuture : batchTaskFutures)
                {
                    if (!emailFuture.isDone())
                    {
                        boolean cancelled = emailFuture.cancel(true);
                        log.error("超时止损：取消未完成的邮件任务，批次号={}，取消结果={}", batchNo, cancelled);
                    }
                }
                throw new CompletionException("批量邮件发送超时/异常：" + e.getMessage(), e);
            });
            return timeoutFuture.thenApply(v -> applicationData);
        }
        catch (Exception e)
        {
            log.error("UAR周期设置：发送邮件：发送标标志sendFlag:{},批量发送邮件失败【批次号：{}】，异步任务执行过程中抛出异常", sendFlag,batchNo, e);
            SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
            sendEmailActionLog.setItCode(itCode);
            sendEmailActionLog.setOperation(sendFlag + "-UAR管理:UAR周期设置:通知邮件异常");
            sendEmailActionLog.setSendFlag(sendFlag);
            sendEmailActionLog.setMessage(e.getMessage());
            sendEmailActionLog.setStackTrace(Arrays.toString(e.getStackTrace()));
            sendEmailActionLog.setBatchNo(batchNo);
            sendEmailActionLog.setCreateDate(LocalDateTime.now());
            sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
            return CompletableFuture.failedFuture(e);
        }
    }

    private boolean isValid(String field) {return field != null && !field.isBlank() && !"0".equals(field);}

    private Map<String, String> getVariables(List<ItsApplicationData> allowSendEmail,List<ApplicationCycleInfoBean> uarNameList,Date dueDate)
    {
        Map<String, String> variables = new HashMap<>();
        StringBuilder cmdbIdBuilder = new StringBuilder();
        StringBuilder appNameBuilder = new StringBuilder();
        String decommissionReason = "";
        String uarName = Optional.ofNullable(uarNameList) // 处理列表为null的情况
        .filter(list -> !CollectionUtils.isEmpty(list)) // 过滤空列表
        .map(list -> list.get(0).getUarName()) // 提取第一个元素的uarName
        .filter(StringUtils::isNotEmpty) // 过滤空字符串（null/空串/纯空格）
        .orElse("未知的周期名称"); // 所有空值场景的兜底值
        for(ItsApplicationData  data : allowSendEmail)
        {
            String cmdbId = data.getCmdbId();
            String appName = data.getApplicationName();
            decommissionReason = data.getDecommissionReason();
            if (cmdbId != null && !cmdbId.trim().isEmpty())
            {
                if (cmdbIdBuilder.length() > 0)
                {
                    cmdbIdBuilder.append(",");
                }
                cmdbIdBuilder.append(cmdbId);
            }
            if (appName != null && !appName.trim().isEmpty())
            {
                if (appNameBuilder.length() > 0)
                {
                    appNameBuilder.append(",");
                }
                appNameBuilder.append(appName);
            }
        }
        variables.put("{appId}", cmdbIdBuilder.toString());
        variables.put("{appName}", appNameBuilder.toString());
        variables.put("{decommissionReason}", StringUtils.isEmpty(decommissionReason) ? "未知" : decommissionReason);
        variables.put("{uarName}", uarName);
        variables.put("{dueDate}", Objects.isNull(dueDate) ? "dateTime" : formatterForUser.format(dueDate));
        return variables;
    }

    /**
     * @Description TODO 给选中应用的负责人发送开启本轮UAR周期的通知邮件
     * @author wangfenglong
     * @date 2026/1/19 16:58
    **/
    @Override
    public UarCycleBean batchSendEmailsAsyncByInitUarCycleAndOfflineTime(UarCycleBean uarCycleBean)
    {
        // 如果是全选的话，则查询出所有符合条件的cmdbId
        if (uarCycleBean.getIsALL())
        {
            List<String> cmdbIdList = getBaseMapper().queryIdListByFilters(uarCycleBean.getFilters());
            uarCycleBean.setCmdbId(cmdbIdList);
        }
        if (uarCycleBean.getCmdbId() == null || uarCycleBean.getCmdbId().isEmpty())
        {
            String infoMsg = "UAR周期设置：发送邮件：开启本轮UAR周期的通知邮件: getCmdbId没有查询到";
            log.info(infoMsg);
            return null;
        }
        return uarCycleBean;
    }

    @Override
    public    List<Map<String ,Object>> uar_cycle_maintenance_distinct_list() {
        return getBaseMapper().uar_cycle_maintenance_distinct_list();
    }

    @Override
    public List<Map<String, Object>> uar_cycle_maintenance_distinct_application() {
        return getBaseMapper().uar_cycle_maintenance_distinct_application();
    }

    @Override
    @Transactional
    public String batchInitUarCycle(UarCycleBean uarCycleBean) {
        // 如果是全选的话，则查询出所有符合条件的cmdbId
        if (uarCycleBean.getIsALL()) {
            List<String> cmdbIdList = getBaseMapper().queryIdListByFilters(uarCycleBean.getFilters());
            uarCycleBean.setCmdbId(cmdbIdList);
        }
        if (uarCycleBean.getCmdbId() == null || uarCycleBean.getCmdbId().isEmpty()) {
            return "无应用数据";
        }

        if (uarCycleBean.getUarId() == null || uarCycleBean.getUarId().isEmpty()) {
            return "请选择周期";
        }

        UarCycleSetting uarCycleSetting = uarCycleSettingMapper.selectById(uarCycleBean.getUarId());
        if (uarCycleSetting == null) {
            return "选择的周期不存在";
        }

        if (uarCycleSetting.getIsCurrent() == 0) {
            return "选择的周期未启用";
        }

        if (uarCycleBean.getAccessReviewScope() == null) {
            return "权限审核范围不能为空";
        }

        LocalDate cycleStartDate = uarCycleBean.getUarCycleStartDate() == null ? uarCycleSetting.getDefaultCycleStartDate() : uarCycleBean.getUarCycleStartDate();
        LocalDate cycleEndDate = uarCycleBean.getUarCycleEndDate() == null ? uarCycleSetting.getDefaultCycleEndDate() : uarCycleBean.getUarCycleEndDate();
        if (cycleStartDate == null || cycleEndDate == null) {
            return "周期起止日期不能为空";
        }
        if (cycleEndDate.isBefore(cycleStartDate)) {
            return "周期结束日期不能早于开始日期";
        }


        String currentUser = SecurityUtils.getCurrentUserId();
        LocalDateTime currentTime = LocalDateTime.now();
        // 组装实体集合 （为了拿到主键ID）
        List<UarCycleMaintenance> uarCycleMaintenanceBeanList = uarCycleBean.getCmdbId().stream().map(cmdbId -> {
            UarCycleMaintenance uarCycleMaintenance = new UarCycleMaintenance();
            uarCycleMaintenance.setCmdbId(cmdbId);
            uarCycleMaintenance.setUarId(uarCycleSetting.getUarId());
            uarCycleMaintenance.setUarName(uarCycleSetting.getUarName());
            uarCycleMaintenance.setAppUarPlatformStatus("Active");
            uarCycleMaintenance.setUarCycleStartDate(cycleStartDate);
            uarCycleMaintenance.setUarCycleEndDate(cycleEndDate);
            uarCycleMaintenance.setAccessReviewScope(uarCycleBean.getAccessReviewScope());
            uarCycleMaintenance.setCreateBy(currentUser);
            uarCycleMaintenance.setCreateTime(currentTime);
            return uarCycleMaintenance;
        }).collect(Collectors.toList());

        // 1. 建周期 绑定应用数据
        boolean batchedInitUarCycle = this.saveOrUpdateBatch(uarCycleMaintenanceBeanList);
        log.info("批量建周期：{}", batchedInitUarCycle);

        // 2. 绑定审核数据。 可能会存在空数据: 先开启 [周期] 后 [Refresh Data]
        int batchedInitUarCycleUserAccessReview = userAccessReviewMapper.batchInitUarCycleUserAccessReview(uarCycleMaintenanceBeanList);
        log.info("批量同步周期成功，影响行数：{}", batchedInitUarCycleUserAccessReview);


        return "success";

    }

    @Override
    @Transactional
    public String batchCompleteCycle(UarCycleBean uarCycleBean) {
        // 如果是全选的话，则查询出所有符合条件的cmdbId
        if (uarCycleBean.getIsALL()) {
            List<String> cmdbIdList = getBaseMapper().queryIdListByFilters(uarCycleBean.getFilters());
            uarCycleBean.setCmdbId(cmdbIdList);
        }
        if (uarCycleBean.getCmdbId() == null || uarCycleBean.getCmdbId().isEmpty()) {
            return "无应用数据";
        }
        // 批量完成周期
        if (getBaseMapper().batchCompleteCycle(uarCycleBean.getCmdbId()) <= 0) {
            log.warn("周期更新失败 , {}", uarCycleBean);
            return "周期更新失败";
        }
        // 1. 给 IT Code 失效的结果
        int updated = userAccessReviewMapper.batchUpdateUserInvalidReviewResult(uarCycleBean.getCmdbId());
        // 2. 给高管数据特殊处理
        updated += userAccessReviewMapper.batchUpdateLmSVPAccessReviewResult(uarCycleBean.getCmdbId());
        // 3. 按审核情况更新结果
        updated += userAccessReviewMapper.batchUpdateFinalReviewDecision(uarCycleBean.getCmdbId());
        if (updated <= 0) {
            return "最终审核结果无变动";
        }
        log.info("最终审核状态已更新");
        return "success";
    }


}
