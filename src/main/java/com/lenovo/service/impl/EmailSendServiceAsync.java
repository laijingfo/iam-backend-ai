package com.lenovo.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.map.MapUtil;
import com.lenovo.async.SendMailTask;
import com.lenovo.bean.PreSendBean;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.config.EmailSendContext;
import com.lenovo.config.GlobalBusinessStatusEnum;
import com.lenovo.dto.BatchSendRequest;
import com.lenovo.dto.BatchSendResult;
import com.lenovo.entity.*;
import com.lenovo.mapper.AutoMailSendLineManagerTempMapper;
import com.lenovo.mapper.DelegationMapper;
import com.lenovo.mapper.ItsApplicationDataMapper;
import com.lenovo.mapper.UserAccessReviewMapper;
import com.lenovo.security.utils.StringUtils;
import com.lenovo.service.*;
import com.lenovo.util.NodeIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import com.lenovo.security.utils.SecurityUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import jakarta.annotation.Resource;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * @Description TODO 邮件发送异步服务
 * @author wangfenglong
 * @date 2025/12/14 01:40
**/
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSendServiceAsync implements IEmailSendService
{
    private final UserAccessReviewService userAccessReviewService;
    private final UarMailTemplateService uarMailTemplateService;
    private final UarMailService uarMailService;
    private final ItsApplicationDataMapper itsApplicationDataMapper;
    private final DelegationMapper delegationMapper;
    private final UseAccessReviewEmailSendService emailSendService;
    private final UserAccessReviewMapper userAccessReviewMapper;
    private final AutoMailSendLineManagerTempMapper autoMailSendLineManagerTempMapper;
    private final NodeIdUtil nodeIdUtil;
    private final SendEmailActionLogService sendEmailActionLogService;
    @Resource(name = "emailSendExecutor")
    private AsyncTaskExecutor emailSendExecutorSchedule;
    @Resource(name = "sendUserEmailExecutor")
    private ThreadPoolTaskExecutor sendUserEmailExecutor;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final SimpleDateFormat formatterForUser = new SimpleDateFormat("yyyy-MM-dd");
    private final SendMailTask sendMailTask;


    /**
     * @Description TODO lm还是bpo？这是个问题 崇拜🤩
     * @param [flag, selectedRecords, successFailed]
     * @author wangfenglong
     * @date 2026/6/11 09:43
    **/
    @Override
    public void saveBpoAndLmInfoToDb(String flag,List<UserAccessReview> selectedRecords,String successFailed,BatchSendRequest request) throws Exception
    {
        if("lm".equals(flag))
        {
            saveLmInfoToDb(selectedRecords,successFailed,request);
        }
        else if("bpo".equals(flag))
        {
            saveBpoInfoToDb(selectedRecords,successFailed);
        }
    }

    /**
     * @Description TODO 将筛选出的Lm信息写入数据库
     * @author wangfenglong
     * @date 2026/6/3 12:53
    **/
    @Async
    public void saveLmInfoToDb(List<UserAccessReview> selectedRecords,String successFailed,BatchSendRequest request)
    {
        try
        {
            int batchSize = 500;
            List<UserAccessReview> allowSendEmail = this.getAllowSendEmailListToLm(selectedRecords,successFailed,request).stream()
            .filter(item -> item.getLineManagerEmail() != null && !item.getLineManagerEmail().trim().isEmpty())
            .collect(Collectors.toList());
            if(CollectionUtil.isEmpty(allowSendEmail))
            {
                throw new Exception("lm没有整合出需要写入到数据库的待发送邮件信息");
            }
            int totalBpo = allowSendEmail.size();

            if("1".equals(successFailed))
            {
                userAccessReviewMapper.truncateAllowSendEmailForLm();
                for(int i = 0; i < totalBpo; i += batchSize)
                {
                    List<UserAccessReview> batchList = allowSendEmail.subList(i, Math.min(i + batchSize, totalBpo));
                    int a = userAccessReviewMapper.batchInsertForLm(batchList);
                }
            }

            if("2".equals(successFailed))
            {
                userAccessReviewMapper.truncateAllowSendEmailForLmFailed();
                for(int i = 0; i < totalBpo; i += batchSize)
                {
                    List<UserAccessReview> batchList = allowSendEmail.subList(i, Math.min(i + batchSize, totalBpo));
                    int a = userAccessReviewMapper.batchInsertForLmFailed(batchList);
                }
            }
        }
        catch (Exception e)
        {
            SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
            sendEmailActionLog.setItCode("UAR");
            sendEmailActionLog.setOperation("-UAR管理:UAR发送邮件:写入lm到数据库出现异常");
            sendEmailActionLog.setSendFlag("");
            sendEmailActionLog.setMessage(e.getMessage());
            sendEmailActionLog.setStackTrace(Arrays.toString(e.getStackTrace()));
            sendEmailActionLog.setBatchNo("");
            sendEmailActionLog.setCreateDate(LocalDateTime.now());
            sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
        }
        log.info("写入邮件给领导看：批量写入邮件:条数据到allow_send_email_for_lm表");
    }

    /**
     * @Description TODO 将筛选出的bpo信息写入数据库
     * @author wangfenglong
     * @date 2026/6/3 13:50
    **/
    @Async
    public void saveBpoInfoToDb(List<UserAccessReview> selectedRecords,String successFailed)
    {
        try
        {
            int batchSize = 500;
            List<UserAccessReview> allowSendEmail = this.getAllowSendEmailListToBpo(selectedRecords,successFailed).stream()
            .filter(item -> item.getBpoEmail() != null && !item.getBpoEmail().trim().isEmpty())
            .collect(Collectors.toList());
            if(CollectionUtil.isEmpty(allowSendEmail))
            {
                throw new Exception("bpo没有整合出需要写入到数据库的待发送邮件信息");
            }
            int totalBpo = allowSendEmail.size();

            if("1".equals(successFailed))
            {
                userAccessReviewMapper.truncateAllowSendEmailForBpo();
                for(int i = 0; i < totalBpo; i += batchSize)
                {
                    List<UserAccessReview> batchList = allowSendEmail.subList(i, Math.min(i + batchSize, totalBpo));
                    int a = userAccessReviewMapper.batchInsertForBpo(batchList);
                }
            }
        }
        catch (Exception e)
        {
            SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
            sendEmailActionLog.setItCode("UAR");
            sendEmailActionLog.setOperation("-UAR管理:UAR发送邮件:写入bpo到数据库出现异常");
            sendEmailActionLog.setSendFlag("");
            sendEmailActionLog.setMessage(e.getMessage());
            sendEmailActionLog.setStackTrace(Arrays.toString(e.getStackTrace()));
            sendEmailActionLog.setBatchNo("");
            sendEmailActionLog.setCreateDate(LocalDateTime.now());
            sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
        }
        log.info("写入邮件给领导看：批量写入邮件::条数据到allow_send_email_for_bpo表");
    }

    /**
     * @Description TODO 处理lm数据
     * @param [selectedRecords, successFailed]
     * @author wangfenglong
     * @date 2026/6/11 09:44
    **/
    public List<UserAccessReview> getAllowSendEmailListToLm(List<UserAccessReview> selectedRecords,String successFailed,BatchSendRequest request) throws Exception
    {
        try
        {
            List<UserAccessReview> allowSendEmail = new ArrayList<>();
            selectedRecords.forEach(record ->
            {
                String rawLm = record.getLineManager();
                if (rawLm != null)
                {
                    record.setLineManager(rawLm.toLowerCase());// 转小写并重新set回去
                }
            });

            /*过滤出来的都是能发送且没有审核通过的*/
            //相同的LineManager分组只发一条,按LineManager分组，并提取每组的lineManagerEmail列表
            Map<String, List<String>> emailByLineManager = selectedRecords.stream()
            .filter(record -> {
                String lineManager = record.getLineManager();
                String lineManagerBandEdFlag = record.getLineManagerBandEdFlag();
                String reviewStatus = record.getLineManagerReviewStatus();
                return (!"2".equals(reviewStatus) && lineManager != null && !lineManager.trim().isEmpty()) && (!"1".equals(lineManagerBandEdFlag));})
            .collect(Collectors.groupingBy(UserAccessReview::getLineManager, Collectors.mapping(UserAccessReview::getLineManagerEmail, Collectors.toList())));

            //构建LineManager发送列表
            Map<String, UserAccessReview> lmRecordMap = selectedRecords.stream().filter(record -> StringUtils.isNotBlank(record.getLineManager())).collect(Collectors.toMap(UserAccessReview::getLineManager, record -> record, (v1, v2) -> v1));
            for(Map.Entry<String, List<String>> entry : emailByLineManager.entrySet())
            {
                UserAccessReview temp = lmRecordMap.get(entry.getKey());
                if (temp != null)
                {
                    UserAccessReview lmTemp = new UserAccessReview();
                    lmTemp.setUuid(temp.getUuid());
                    lmTemp.setSequenceNumber(temp.getSequenceNumber());
                    lmTemp.setLineManager(temp.getLineManager());
                    lmTemp.setLineManagerReviewStatus(temp.getLineManagerReviewStatus());
                    lmTemp.setLineManagerEmail(temp.getLineManagerEmail());
                    lmTemp.setItCodeOfUser(temp.getItCodeOfUser());
                    lmTemp.setUserName(temp.getUserName());
                    lmTemp.setAppName(temp.getAppName());
                    lmTemp.setDepartment(temp.getDepartment());
                    lmTemp.setUarId(temp.getUarId());
                    lmTemp.setLineManagerBandEdFlag(temp.getLineManagerBandEdFlag());
                    lmTemp.setCmdbId(temp.getCmdbId());
                    lmTemp.setLineManagerLevelCode(temp.getLineManagerLevelCode());
                    allowSendEmail.add(lmTemp);
                }
            }

            if(CollectionUtil.isEmpty(allowSendEmail))
            {
                log.info("处理lm数据为空,没有需要发送的邮件.");
                return null;
            }

            if("1".equals(successFailed))
            {
                UseAccessReviewBean bean = this.getCurrentRequestFilters(request);

                Map<String, Map<String,String>> ccEmailsForLm = userAccessReviewMapper.getCcEmailByLineManagerNew(allowSendEmail,bean);
                if(MapUtil.isNotEmpty(ccEmailsForLm))
                {
                    allowSendEmail.forEach(item ->
                    {
                        Map<String, String> row = ccEmailsForLm.get(item.getLineManager());
                        if(row != null)
                        {
                            item.setCcEmail(row.get("email"));
                        }
                    });
                }
            }

            List<String> svpList1 = this.getUserBySVPBandFlag("1");
            Set<String> svpList = new HashSet<>(svpList1);
            if(CollectionUtil.isNotEmpty(svpList1))
            {
                allowSendEmail = allowSendEmail.stream().filter(temp -> !(svpList.contains(temp.getBpo()) || svpList.contains(temp.getLineManager()))).collect(Collectors.toList());
            }
            log.info("待发送页面-发送邮件开始：SVP过滤前{}条，过滤后{}条，最终需要发送的邮件数量：{}", allowSendEmail.size(), allowSendEmail.size(), allowSendEmail.size());
            return allowSendEmail;
        }
        catch (Exception e)
        {
            log.error("待发送页面：处理lm数据出现异常:", e);
            throw new Exception("处理lm数据出现异常: {}", e);
        }
    }

    public UseAccessReviewBean getCurrentRequestFilters(BatchSendRequest request)
    {
        Map<String, Object> filterMap = request.getFilters();
        UseAccessReviewBean  bean = new UseAccessReviewBean();

        //应用编号
        Object cmdbId = filterMap.get("cmdbId");
        if(cmdbId != null && !cmdbId.toString().isBlank())
        {
            List<String> cmdbList = Arrays.stream(cmdbId.toString().split(","))
                    .map(String::trim)
                    .filter(trimmedId -> !trimmedId.isEmpty())
                    .collect(Collectors.toList());
            bean.setCmdbIdListOfChoose(cmdbList);
        }

        //应用名称
        Object appName = filterMap.get("appName");
        if(appName != null && !appName.toString().isBlank())
        {
            List<String> appNameList = Arrays.stream(appName.toString().split(","))
                    .map(String::trim)
                    .filter(trimmedId -> !trimmedId.isEmpty())
                    .collect(Collectors.toList());
            bean.setAppNameListOfChoose(appNameList);
        }

        //用户itCode
        Object itCodeOfUser = filterMap.get("itCodeOfUser");
        if(itCodeOfUser != null && !itCodeOfUser.toString().isBlank())
        {
            bean.setItCodeOfUser(itCodeOfUser.toString());
        }

        //直属经理
        Object lineManager = filterMap.get("lineManager");
        if(lineManager != null && !lineManager.toString().isBlank())
        {
            bean.setLineManager(lineManager.toString());
        }

        //直属经理审核状态
        Object lineManagerReviewStatus = filterMap.get("lineManagerReviewStatus");
        if (lineManagerReviewStatus != null && !lineManagerReviewStatus.toString().isBlank())
        {
            bean.setLineManagerReviewStatus(lineManagerReviewStatus.toString());
        }

        //bpo
        Object bpo = filterMap.get("bpo");
        if(bpo != null && !bpo.toString().isBlank())
        {
            bean.setBpo(bpo.toString());
        }

        //bpo审核状态
        Object bpoReviewStatus = filterMap.get("bpoReviewStatus");
        if (bpoReviewStatus != null && !bpoReviewStatus.toString().isBlank())
        {
            bean.setBpoReviewStatus(bpoReviewStatus.toString());
        }

        //直属经理部门
        Object dept = filterMap.get("dept");
        if (dept != null && !dept.toString().isBlank())
        {
            bean.setDept(dept.toString());
        }

        //直属经理职级
        Object lineManagerLevelCode = filterMap.get("lineManagerLevelCode");
        if (lineManagerLevelCode != null && !lineManagerLevelCode.toString().isBlank())
        {
            bean.setLineManagerLevelCode(lineManagerLevelCode.toString());
        }
        return bean;
    }

    /**
     * @Description TODO 处理bpo数据
     * @param [selectedRecords, successFailed]
     * @author wangfenglong
     * @date 2026/6/11 09:45
    **/
    public List<UserAccessReview> getAllowSendEmailListToBpo(List<UserAccessReview> selectedRecords,String successFailed) throws Exception
    {
        try
        {
            List<UserAccessReview> allowSendEmail = new ArrayList<>();
            List<UserAccessReview> allowBpoEmailList = new ArrayList<>();

            //过滤出来的都是能发送且没有审核通过的
            //相同的bpo分组只发一条,按bpo分组，并提取每组的bpo列表
            Map<String, List<String>> emailByBpo = selectedRecords.stream()
            .filter(record -> {
                String bpo = record.getBpo();
                String bpoBandEdFlag = record.getBpoBandEdFlag();
                String reviewStatus = record.getBpoReviewStatus();
                return (!"2".equals(reviewStatus) && bpo != null && !bpo.trim().isEmpty()) && (!"1".equals(bpoBandEdFlag));})
            .collect(Collectors.groupingBy(UserAccessReview::getBpo, Collectors.mapping(UserAccessReview::getBpoEmail, Collectors.toList())));

            //构建BPO发送列表
            Map<String, UserAccessReview> bpoRecordMap = selectedRecords.stream().filter(record -> StringUtils.isNotBlank(record.getBpo())).collect(Collectors.toMap(UserAccessReview::getBpo, record -> record, (v1, v2) -> v1));
            for (Map.Entry<String, List<String>> entry : emailByBpo.entrySet())
            {
                String bpoKey = entry.getKey();
                UserAccessReview temp = bpoRecordMap.get(bpoKey);
                if (temp == null) continue;
                if (bpoKey.contains(";"))
                {
                    try
                    {
                        //Map<String, String> BpoMap = StringUtils.getebaySendMailToBpo(temp);
                        Map<String, String> BpoMap = StringUtils.getebaySendMailToBpoNotCheckBpoBandEdFlag(temp);
                        if (MapUtil.isNotEmpty(BpoMap))
                        {
                            BpoMap.forEach((k, v) ->
                            {
                                UserAccessReview bposplit = new UserAccessReview();
                                bposplit.setUuid(temp.getUuid());
                                bposplit.setItCodeOfUser(temp.getItCodeOfUser());
                                bposplit.setUserName(temp.getUserName());
                                bposplit.setAppName(temp.getAppName());
                                bposplit.setDepartment(temp.getDepartment());
                                bposplit.setSequenceNumber(temp.getSequenceNumber());
                                bposplit.setBpoReviewStatus(temp.getBpoReviewStatus());
                                bposplit.setCmdbId(temp.getCmdbId());
                                bposplit.setBpo(k);
                                bposplit.setBpoEmail(v);
                                bposplit.setBpoBandEdFlag("split");
                                //bposplit.setCcEmail(new ArrayList<String>(Arrays.asList("0")));
                                allowBpoEmailList.add(bposplit);
                            });
                        }
                    }
                    catch (Exception e)
                    {
                        log.error("正常发送邮件_拆分BPO多邮箱失败", e);
                        temp.setBpoBandEdFlag("Error");
                        temp.setSequenceNumber(temp.getSequenceNumber());
                        allowBpoEmailList.add(temp);
                    }
                }
                else
                {
                    UserAccessReview bpoTemp = new UserAccessReview();
                    bpoTemp.setUuid(temp.getUuid());
                    bpoTemp.setItCodeOfUser(temp.getItCodeOfUser());
                    bpoTemp.setUserName(temp.getUserName());
                    bpoTemp.setAppName(temp.getAppName());
                    bpoTemp.setDepartment(temp.getDepartment());
                    bpoTemp.setSequenceNumber(temp.getSequenceNumber());
                    bpoTemp.setBpo(temp.getBpo());
                    bpoTemp.setBpoReviewStatus(temp.getBpoReviewStatus());
                    bpoTemp.setBpoEmail(temp.getBpoEmail());
                    bpoTemp.setUarId(temp.getUarId());
                    bpoTemp.setBpoBandEdFlag(StringUtils.isBlank(temp.getBpoBandEdFlag()) ? "0" : temp.getBpoBandEdFlag());
                    bpoTemp.setCmdbId(temp.getCmdbId());
                    allowBpoEmailList.add(bpoTemp);
                }
            }

            if(CollectionUtil.isEmpty(allowBpoEmailList))
            {
                log.info("处理bpo数据是空.");
                return null;
            }

            //过滤有效数据 + 按纯净Bpo值 分组 (保证分组顺序=原始顺序)
            Map<String, List<UserAccessReview>> bpoGroup = allowBpoEmailList.stream()
            .filter(item -> item.getBpo() != null && !item.getBpo().trim().isEmpty())
            .collect(Collectors.groupingBy(
            item -> item.getBpo().trim(),  // 分组KEY：去首尾空格的纯净Bpo值
            LinkedHashMap::new,                             // 核心必加：保证分组顺序不变，同Bpo数据连续排列
            Collectors.toList()));                          // 同Bpo的所有数据存入List，保留全部

            //遍历分组，封装【Bpo + BpoEmail】到allowSendEmail集合
            for (Map.Entry<String, List<UserAccessReview>> entry : bpoGroup.entrySet())
            {
                UserAccessReview newObj = new UserAccessReview();
                newObj.setBpo(entry.getKey());
                String targetBpoEmail = entry.getValue().stream().filter(Objects::nonNull).map(UserAccessReview::getBpoEmail).filter(email -> email != null && !email.trim().isEmpty()).findFirst().orElse(null);
                String sequenceNumber = entry.getValue().stream().filter(Objects::nonNull).map(UserAccessReview::getSequenceNumber).filter(sequenceNum -> sequenceNum != null && !sequenceNum.trim().isEmpty()).findFirst().orElse(null);
                String bpoBandFlag = entry.getValue().stream().filter(Objects::nonNull).map(UserAccessReview::getBpoBandEdFlag).filter(Objects::nonNull).findFirst().orElse("");
                String uuid = entry.getValue().stream().filter(Objects::nonNull).map(UserAccessReview::getUuid).filter(Objects::nonNull).findFirst().orElse(newObj.getUuid());
                String bpoReviewStatus = entry.getValue().stream().filter(Objects::nonNull).map(UserAccessReview::getBpoReviewStatus).filter(Objects::nonNull).findFirst().orElse("");
                newObj.setBpoEmail(targetBpoEmail);
                newObj.setSequenceNumber(sequenceNumber);
                newObj.setBpoBandEdFlag(bpoBandFlag);
                newObj.setUuid(uuid);
                newObj.setBpoReviewStatus(bpoReviewStatus);
                newObj.setDepartment(entry.getValue().stream()
                        .filter(Objects::nonNull)
                        .map(UserAccessReview::getDepartment)
                        .filter(Objects::nonNull)
                        .filter(dept -> dept.contains("delegatee"))
                        .findFirst().orElse(""));
                allowSendEmail.add(newObj);
            }

            if("1".equals(successFailed))
            {
                //处理delegation的bpo
                Map<String, Map<String,String>> ccEmailsForLm = delegationMapper.getDelegateeByBpo(allowSendEmail);
                //取出已存在的bpo集合，用于快速判断是否存在该bpo
                Set<String> existBpoSet = allowSendEmail.stream().map(UserAccessReview::getBpo).collect(Collectors.toSet());
                //遍历查询出来的代理数据
                for (Map.Entry<String, Map<String, String>> entry : ccEmailsForLm.entrySet())
                {
                    String delegateeCode = entry.getKey();
                    Map<String, String> dataMap = entry.getValue();
                    //不在原有集合里，新增对象塞入list
                    if (!existBpoSet.contains(delegateeCode))
                    {
                        UserAccessReview newItem = new UserAccessReview();
                        newItem.setBpo(delegateeCode);
                        String emailStr = dataMap.get("email");
                        newItem.setBpoEmail(emailStr);
                        newItem.setDepartment("delegatee");
                        newItem.setSequenceNumber(UarMailServiceImpl.generateUarSequence());
                        allowSendEmail.add(newItem);
                    }
                }
            }

            //allowSendEmail = processEmailList(allowSendEmail);
            List<String> svpList1 = this.getUserBySVPBandFlag("1");
            Set<String> svpList = new HashSet<>(svpList1);
            if(CollectionUtil.isNotEmpty(svpList1))
            {
                allowSendEmail = allowSendEmail.stream().filter(temp -> !(svpList.contains(temp.getBpo()) || svpList.contains(temp.getLineManager()))).collect(Collectors.toList());
            }

            //****************************处理分组后的每个bpo关联对应的所有的sequenceNumber****************************//
            /*Map<String, List<String>> bpoSeqMap = selectedRecords.stream() //按bpo分组，提前过滤null实体、null序列号
            .filter(Objects::nonNull)
            .filter(record -> Objects.nonNull(record.getSequenceNumber())) // 过滤sequence为null的记录，后续无需重复过滤
            .collect(Collectors.groupingBy(
            UserAccessReview::getBpo,
            Collectors.mapping(UserAccessReview::getSequenceNumber, Collectors.toList())));

            Map<String, List<String>> bpoUuidMap = selectedRecords.stream()
            .filter(Objects::nonNull)
            .filter(record -> Objects.nonNull(record.getUuid()))
            .collect(Collectors.groupingBy(
            UserAccessReview::getBpo,
            Collectors.mapping(UserAccessReview::getUuid, Collectors.toList())));

            //2.遍历allowSendEmail，每个item只绑定自身BPO对应的序列号
            for (UserAccessReview item : allowSendEmail)
            {
                if (item == null) continue;
                String bpo = item.getBpo();
                if (Objects.isNull(bpo))
                {
                    log.warn("UserAccessReview BPO字段为空，跳过赋值");
                    item.setLeitSystemId("");
                    continue;
                }
                // 获取当前BPO对应的序列号列表
                List<String> currentBpoSeqList = bpoSeqMap.get(bpo);
                List<String> currentBpoUuidList = bpoUuidMap.get(bpo);
                String leitSystemId;
                String uuidString;
                if(currentBpoSeqList == null || currentBpoSeqList.isEmpty())
                {
                    leitSystemId = "";
                }
                else
                {
                    // 逗号拼接当前BPO专属序列号
                    leitSystemId = String.join(",", currentBpoSeqList);
                }

                if(currentBpoUuidList == null || currentBpoUuidList.isEmpty())
                {
                    uuidString = "";
                }
                else
                {
                    // 逗号拼接当前BPO专属UUid
                    uuidString = String.join(",", currentBpoUuidList);
                }
                item.setDistinctBpoSequenceNumber(leitSystemId);// 给当前item赋值，只绑定当前BPO的序列
                item.setDistinctBpoUuid(uuidString); //当前bpo的uuid序列
            }
            //数据库增加字段
            //ALTER TABLE "allow_send_email_for_bpo" ADD COLUMN "distinct_bpo_sequence_number" text;
            //ALTER TABLE "allow_send_email_for_bpo" ADD COLUMN "distinct_bpo_uuid" text;
            //UserAccessReview表和AllowSendEmailForBpo表增加两个字段：
            //@TableField(exist = false)
            //private String distinctBpoSequenceNumber;
            //@TableField(exist = false)
            //private String distinctBpoUuid;
            //mapper:batchInsertForBpo增加两个字段：#{item.distinctBpoSequenceNumber},#{item.distinctBpoUuid}*/
            //****************************处理分组后的每个bpo关联对应的所有的sequenceNumber****************************//

            log.info("待发送页面-发送邮件开始：SVP过滤前{}条，过滤后{}条，最终需要发送的邮件数量：{}", allowSendEmail.size(), allowSendEmail.size(), allowSendEmail.size());
            return allowSendEmail;
        }
        catch (Exception e)
        {
            log.error("待发送页面-处理Bpo数据异常:", e);
            throw new Exception("处理bpo数据出现异常: {}" , e);
        }
    }









    /**
     * @Description TODO 待发送页面-将需要发送的邮件写入到数据库给领导看（点击取消按钮调用）
     * @author wangfenglong
     * @date 2026/1/9 16:25
     **/
    @Async
    @Override
    public void getAllowSendEmailForLead(List<UserAccessReview> selectedRecords) throws Exception
    {
        int batchSize = 500;
        List<UserAccessReview> allowSendEmail = this.getAllowSendEmaiList(selectedRecords);
        if(CollectionUtil.isEmpty(allowSendEmail))
        {
            throw new Exception("没有整合出需要写入到数据库的待发送邮件信息");
        }
        int totalBpo = allowSendEmail.size();
        userAccessReviewMapper.truncateTempAllowSendTable();
        for(int i = 0; i < totalBpo; i += batchSize)
        {
            List<UserAccessReview> batchList = allowSendEmail.subList(i, Math.min(i + batchSize, totalBpo));
            int a = userAccessReviewMapper.batchInsert(batchList);
        }
        log.info("写入邮件给领导看：批量写入邮件::成功插入 {} 条数据到allow_send_email_include_lm_and_bpo表", totalBpo);
    }

    /**
     * @Description TODO 加工数据：获取需要发送的邮件(BPO和Lm的邮件信息)
     * @author wangfenglong
     * @date 2026/1/16 09:43
     **/
    @Override
    public List<UserAccessReview> getAllowSendEmaiList(List<UserAccessReview> selectedRecords) throws Exception
    {
        try
        {
            List<BatchSendResult> results = new ArrayList<>();
            List<UserAccessReview> allowSendEmail = new ArrayList<>();
            List<UserAccessReview> allowBpoEmailList = new ArrayList<>();

            /*过滤出来的都是能发送且没有审核通过的*/
            //相同的LineManager分组只发一条,按LineManager分组，并提取每组的lineManagerEmail列表
            Map<String, List<String>> emailByLineManager = selectedRecords.stream()
            .filter(record -> {
                String lineManager = record.getLineManager();
                String lineManagerBandEdFlag = record.getLineManagerBandEdFlag();
                String reviewStatus = record.getLineManagerReviewStatus();
                return (!"2".equals(reviewStatus) && lineManager != null && !lineManager.trim().isEmpty()) && (!"1".equals(lineManagerBandEdFlag));})
            .collect(Collectors.groupingBy(UserAccessReview::getLineManager, Collectors.mapping(UserAccessReview::getLineManagerEmail, Collectors.toList())));

            /*过滤出来的都是能发送且没有审核通过的*/
            //相同的bpo分组只发一条,按bpo分组，并提取每组的bpo列表
            Map<String, List<String>> emailByBpo = selectedRecords.stream()
            .filter(record -> {
                String bpo = record.getBpo();
                String bpoBandEdFlag = record.getBpoBandEdFlag();
                String reviewStatus = record.getBpoReviewStatus();
                return (!"2".equals(reviewStatus) && bpo != null && !bpo.trim().isEmpty()) && (!"1".equals(bpoBandEdFlag));})
            .collect(Collectors.groupingBy(UserAccessReview::getBpo, Collectors.mapping(UserAccessReview::getBpoEmail, Collectors.toList())));

            //构建LineManager发送列表
            Map<String, UserAccessReview> lmRecordMap = selectedRecords.stream().filter(record -> StringUtils.isNotBlank(record.getLineManager())).collect(Collectors.toMap(UserAccessReview::getLineManager, record -> record, (v1, v2) -> v1));
            for(Map.Entry<String, List<String>> entry : emailByLineManager.entrySet())
            {
                UserAccessReview temp = lmRecordMap.get(entry.getKey());
                if (temp != null)
                {
                    List<String> ccEmails = userAccessReviewMapper.getCcEmailByLineManager(temp.getLineManager());
                    UserAccessReview lmTemp = new UserAccessReview();
                    lmTemp.setUuid(temp.getUuid());
                    lmTemp.setSequenceNumber(temp.getSequenceNumber());
                    lmTemp.setLineManager(temp.getLineManager());
                    lmTemp.setLineManagerReviewStatus(temp.getLineManagerReviewStatus());
                    lmTemp.setLineManagerEmail(temp.getLineManagerEmail());
                    lmTemp.setItCodeOfUser(temp.getItCodeOfUser());
                    lmTemp.setUserName(temp.getUserName());
                    lmTemp.setAppName(temp.getAppName());
                    lmTemp.setDepartment(temp.getDepartment());
                    lmTemp.setUarId(temp.getUarId());
                    lmTemp.setLineManagerBandEdFlag(temp.getLineManagerBandEdFlag());
                    lmTemp.setCmdbId(temp.getCmdbId());
                    lmTemp.setLineManagerLevelCode(temp.getLineManagerLevelCode());
                    lmTemp.setCcEmail(CollectionUtils.isEmpty(ccEmails) ? null : String.join(",", ccEmails));
                    allowSendEmail.add(lmTemp);
                }
            }

            //构建BPO发送列表
            Map<String, UserAccessReview> bpoRecordMap = selectedRecords.stream().filter(record -> StringUtils.isNotBlank(record.getBpo())).collect(Collectors.toMap(UserAccessReview::getBpo, record -> record, (v1, v2) -> v1));
            for (Map.Entry<String, List<String>> entry : emailByBpo.entrySet())
            {
                String bpoKey = entry.getKey();
                UserAccessReview temp = bpoRecordMap.get(bpoKey);
                if (temp == null) continue;
                if (bpoKey.contains(";"))
                {
                    try
                    {
                        //Map<String, String> BpoMap = StringUtils.getebaySendMailToBpo(temp);
                        Map<String, String> BpoMap = StringUtils.getebaySendMailToBpoNotCheckBpoBandEdFlag(temp);
                        if (MapUtil.isNotEmpty(BpoMap))
                        {
                            BpoMap.forEach((k, v) ->
                            {
                                UserAccessReview bposplit = new UserAccessReview();
                                bposplit.setUuid(temp.getUuid());
                                bposplit.setItCodeOfUser(temp.getItCodeOfUser());
                                bposplit.setUserName(temp.getUserName());
                                bposplit.setAppName(temp.getAppName());
                                bposplit.setDepartment(temp.getDepartment());
                                bposplit.setSequenceNumber(temp.getSequenceNumber());
                                bposplit.setBpoReviewStatus(temp.getBpoReviewStatus());
                                bposplit.setCmdbId(temp.getCmdbId());
                                bposplit.setBpo(k);
                                bposplit.setBpoEmail(v);
                                bposplit.setBpoBandEdFlag("split");
                                //bposplit.setCcEmail(new ArrayList<String>(Arrays.asList("0")));
                                allowBpoEmailList.add(bposplit);
                            });
                        }
                    }
                    catch (Exception e)
                    {
                        log.error("正常发送邮件_拆分BPO多邮箱失败", e);
                        temp.setBpoBandEdFlag("Error");
                        temp.setSequenceNumber(temp.getSequenceNumber());
                        allowBpoEmailList.add(temp);
                    }
                }
                else
                {
                    UserAccessReview bpoTemp = new UserAccessReview();
                    bpoTemp.setUuid(temp.getUuid());
                    bpoTemp.setItCodeOfUser(temp.getItCodeOfUser());
                    bpoTemp.setUserName(temp.getUserName());
                    bpoTemp.setAppName(temp.getAppName());
                    bpoTemp.setDepartment(temp.getDepartment());
                    bpoTemp.setSequenceNumber(temp.getSequenceNumber());
                    bpoTemp.setBpo(temp.getBpo());
                    bpoTemp.setBpoReviewStatus(temp.getBpoReviewStatus());
                    bpoTemp.setBpoEmail(temp.getBpoEmail());
                    bpoTemp.setUarId(temp.getUarId());
                    bpoTemp.setBpoBandEdFlag(StringUtils.isBlank(temp.getBpoBandEdFlag()) ? "0" : temp.getBpoBandEdFlag());
                    bpoTemp.setCmdbId(temp.getCmdbId());
                    //bpoTemp.setCcEmail(new ArrayList<String>(Arrays.asList("0")));
                    allowBpoEmailList.add(bpoTemp);
                }
            }

            if(CollectionUtil.isEmpty(allowSendEmail) && CollectionUtil.isEmpty(allowBpoEmailList))
            {
                log.info("allowSendEmail=null,没有需要发送的邮件.");
                return null;
            }

            //过滤有效数据 + 按纯净Bpo值 分组 (保证分组顺序=原始顺序)
            Map<String, List<UserAccessReview>> bpoGroup = allowBpoEmailList.stream()
            .filter(item -> item.getBpo() != null && !item.getBpo().trim().isEmpty())
            .collect(Collectors.groupingBy(
                    item -> item.getBpo().trim(), // 分组KEY：去首尾空格的纯净Bpo值
                    LinkedHashMap::new,                             // 核心必加：保证分组顺序不变，同Bpo数据连续排列
                    Collectors.toList()                             // 同Bpo的所有数据存入List，保留全部
            ));

            //遍历分组，封装【Bpo + BpoEmail】到allowSendEmail集合
            for (Map.Entry<String, List<UserAccessReview>> entry : bpoGroup.entrySet())
            {
                UserAccessReview newObj = new UserAccessReview();
                newObj.setBpo(entry.getKey());
                String targetBpoEmail = entry.getValue().stream().filter(Objects::nonNull).map(UserAccessReview::getBpoEmail).filter(email -> email != null && !email.trim().isEmpty()).findFirst().orElse(null);
                String sequenceNumber = entry.getValue().stream().filter(Objects::nonNull).map(UserAccessReview::getSequenceNumber).filter(sequenceNum -> sequenceNum != null && !sequenceNum.trim().isEmpty()).findFirst().orElse(null);
                String bpoBandFlag = entry.getValue().stream().filter(Objects::nonNull).map(UserAccessReview::getBpoBandEdFlag).findFirst().orElse(null);
                newObj.setBpoEmail(targetBpoEmail);
                newObj.setSequenceNumber(sequenceNumber);
                newObj.setBpoBandEdFlag(bpoBandFlag);
                //newObj.setCcEmail(new ArrayList<String>(Arrays.asList("0")));
                allowSendEmail.add(newObj);
            }
            //allowSendEmail = processEmailList(allowSendEmail);

            List<String> svpList1 = this.getUserBySVPBandFlag("1");
            Set<String> svpList = new HashSet<>(svpList1);
            if(CollectionUtil.isNotEmpty(svpList1))
            {
                allowSendEmail = allowSendEmail.stream().filter(temp -> !(svpList.contains(temp.getBpo()) || svpList.contains(temp.getLineManager()))).collect(Collectors.toList());
            }
            log.info("待发送页面-发送邮件开始：SVP过滤前{}条，过滤后{}条，最终需要发送的邮件数量：{}", allowSendEmail.size(), allowSendEmail.size(), allowSendEmail.size());
            return allowSendEmail;
        }
        catch (Exception e)
        {
            log.error("待发送页面-发送邮件开始：发送邮件实际逻辑:校验失败:", e);
            throw new Exception("批量发送邮件核心逻辑执行失败: " +  e);
        }
    }


    //因为数据问题，名字不同，邮箱相同，这个方法过滤重复的邮箱。如果数据没问题的话，不需要这个方法
    private List<UserAccessReview> processEmailList(List<UserAccessReview> allowSendEmail)
    {
        // 1. 初始化结果集和邮箱去重集合（Set保证元素唯一，效率O(1)）
        List<UserAccessReview> resultList = new ArrayList<>();
        Set<String> existEmails = new HashSet<>();

        // 2. 遍历原始集合，逐行处理
        for (UserAccessReview review : allowSendEmail)
        {
            // 跳过null对象，避免空指针
            if (review == null)
            {
                continue;
            }

            String bpoEmail = review.getBpoEmail();
            String lineManagerEmail = review.getLineManagerEmail();

            // 规则1：如果BpoEmail和LineManagerEmail相等，直接跳过（删除该条数据）
            if (bpoEmail != null && bpoEmail.equals(lineManagerEmail))
            {
                continue;
            }
            // 补充：两个邮箱都为null的情况，也视为相等，跳过
            if (bpoEmail == null && lineManagerEmail == null)
            {
                continue;
            }

            // 规则2：检查两个邮箱是否已存在于去重集合中
            boolean bpoExist = (bpoEmail != null) && existEmails.contains(bpoEmail);
            boolean lineManagerExist = (lineManagerEmail != null) && existEmails.contains(lineManagerEmail);

            // 若两个邮箱都未重复，则保留该记录，并将邮箱加入去重集合
            if (!bpoExist && !lineManagerExist)
            {
                resultList.add(review);
                // 非空邮箱加入去重集合
                if (bpoEmail != null)
                {
                    existEmails.add(bpoEmail);
                }
                if (lineManagerEmail != null)
                {
                    existEmails.add(lineManagerEmail);
                }
            }
        }
        return resultList;
    }

    /**
     * @Description TODO 写入到邮件发送表use_access_review_email_send
     * @author wangfenglong
     * @date 2026/1/9 13:13
     **/
    private void recordEmailSendStatus(String uuid, Integer round, String recipientType, String recipientEmail, Long templateId, String status, String errorMsg, String batchNo)
    {
        UseAccessReviewEmailSend sendRecord = new UseAccessReviewEmailSend();
        sendRecord.setReviewUuid(uuid);
        sendRecord.setSendRound(round);
        sendRecord.setSendTime(LocalDateTime.now());
        sendRecord.setSendStatus(status);
        sendRecord.setRecipientType(recipientType);
        sendRecord.setRecipientEmail(recipientEmail);
        sendRecord.setTemplateId(templateId);
        sendRecord.setErrorMessage(errorMsg);
        sendRecord.setBatchNo(batchNo);
        emailSendService.save(sendRecord);
    }

    /**
     * @Description TODO 查询ad_tb_upp_nature_2用户表获取高管
     * @author wangfenglong
     * @date 2025/12/30 10:48
     **/
    @Override
    public List<String> getUserBySVPBandFlag(String flag)
    {
        return autoMailSendLineManagerTempMapper.getUserBySVPBandFlag(flag);
    }

    /**
     * @Description TODO 生成批次号
     * @author wangfenglong
     * @date 2026/1/9 13:08
     **/
     public static String generateBatchNo(String itCode,String sendFlag)
    {
        String batchNo;
        try
        {
            String currentDate = LocalDateTime.now().format(formatter);
            int randomNum = 10000 + ThreadLocalRandom.current().nextInt(90000);
            if (itCode == null || itCode.trim().isEmpty())
            {
                itCode = "DEFAULT_" + ThreadLocalRandom.current().nextInt(100000);
                log.error("当前用户IT编码为空，使用默认值：{}", itCode);
            }
            else
            {
                itCode = itCode.trim(); // 去除首尾空格，避免无效字符
            }
            //拼接批次号（格式：IT编码_时间戳_5位随机数）
            batchNo = String.format("%s_%s_%s_%d", sendFlag,itCode, currentDate, randomNum);
        }
        catch (Exception e)
        {
            log.error("生成批次号异常", e);
            batchNo = UUID.randomUUID().toString().replace("-", "");
        }
        return batchNo;
    }

    /**
     * @Description TODO 用户发邮件--获取【最终审核是移除的用户】数据
     * @author wangfenglong
     * @date 2026/5/9 14:41
     **/
    @Override
    public List<UserAccessReview> getFinalRemoveUser(UseAccessReviewBean useAccessReviewBean,String itCode,String sendFlag,String roleFlag)
    {
        try
        {
            // 去重逻辑：同一个 line_manager_email，只给第一个 / 任意一个 it_code_of_user 赋值，其他相同 line_manager 的都设为空。
            List<UserAccessReview> userList = userAccessReviewMapper.getFinalRemoveUser(useAccessReviewBean,roleFlag);
            return Optional.ofNullable(userList).orElse(List.of());

            /*return userList.stream()
            .filter(Objects::nonNull)
            .filter(user -> user.getEmail() != null && !user.getEmail().trim().isEmpty())
            .collect(Collectors.groupingBy(
            user -> user.getEmail().trim().toLowerCase(),  // 按【小写邮箱】去重
            LinkedHashMap::new,
            Collectors.toList()
            )).values().stream()
            .map(group -> group.get(0)) // 每组取第一个
            .peek(user -> user.setEmail(user.getEmail().trim().toLowerCase()))
            .collect(Collectors.toList());*/
        }
        catch (Exception e)
        {
            log.error("查询最终审核是移除的用户数据出现异常:{}",e.getMessage(),e);
            SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
            sendEmailActionLog.setItCode(itCode);
            sendEmailActionLog.setOperation(sendFlag + "-UAR管理:UAR发送用户邮件:查询最终审核是移除的用户数据");
            sendEmailActionLog.setSendFlag(sendFlag);
            sendEmailActionLog.setMessage(e.getMessage());
            sendEmailActionLog.setStackTrace(Arrays.toString(e.getStackTrace()));
            sendEmailActionLog.setBatchNo("暂无");
            sendEmailActionLog.setCreateDate(LocalDateTime.now());
            sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
            return List.of();
        }
    }

    /**
     * @Description TODO 用户发邮件--将【最终审核是移除的用户】写入到allow_send_email_include_final_remove_user表
     * @author wangfenglong
     * @date 2026/5/9 14:59
    **/
    @Async
    @Override
    public void saveFinalRemoveUserToAllowSendEmailIncludeFinalRemoveUserData(List<UserAccessReview> newUserList,String itCode,String sendFlag)
    {
        try
        {
            int batchSize = 500;
            int totalBpo = newUserList.size();
            userAccessReviewMapper.truncateTempAllowUserSendTableForFinalRemove();
            for(int i = 0; i < totalBpo; i += batchSize)
            {
                List<UserAccessReview> batchList = newUserList.subList(i, Math.min(i + batchSize, totalBpo));
                int a = userAccessReviewMapper.batchInsertAllowUserIncludeFinalRemove(batchList);
            }
            log.info("写入最终审核是移除的用户数据给领导看：批量写入用户邮件::成功插入 {} 条数据到allow_send_email_include_final_remove_user表", totalBpo);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            log.error("写入最终审核是移除的用户到表中出现异常:{}",e.getMessage(),e);
            SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
            sendEmailActionLog.setItCode(itCode);
            sendEmailActionLog.setOperation(sendFlag + "-UAR管理:UAR发送用户邮件:写入最终审核是移除的用户到表中");
            sendEmailActionLog.setSendFlag(sendFlag);
            sendEmailActionLog.setMessage(e.getMessage());
            sendEmailActionLog.setStackTrace(Arrays.toString(e.getStackTrace()));
            sendEmailActionLog.setBatchNo("暂无");
            sendEmailActionLog.setCreateDate(LocalDateTime.now());
            sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
        }
    }


    /**
     * @Description TODO 用户发邮件--查询final_review_decision最终审核是空的用户数据
     * @author wangfenglong
     * @date 2026/3/12 18:19
    **/
    @Override
    public List<String>  getUserByFinalReviewDecisionIsEmpty(UseAccessReviewBean useAccessReviewBean,String itCode,String sendFlag,String roleFlag)
    {
        try
        {
            String flag = "";
            List<String> svpList = this.getUserBySVPBandFlag("1");
            List<UserAccessReview> userList = userAccessReviewMapper.getItCodeUserByFinalReviewDecisionIsEmpty(useAccessReviewBean,roleFlag);
            //List<String> uppNatureUserList = userAccessReviewMapper.getUserByBlow(flag);
            List<String> svpList1 = this.getUserBySVPBandFlag("1");
            if(CollectionUtil.isEmpty(userList) || CollectionUtil.isEmpty(svpList))
            {
                log.error("查询final_review_decision最终审核是空的用户数据:没有查询到信息");
                return List.of();
            }

            //Set.contains() 是哈希查找（时间复杂度 O (1)），而 List.contains() 是遍历查找（O (n)），数据量大时（如万级）效率提升显著
            Set<String> uppNatureUserSet = svpList.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(String::toLowerCase) // 转小写
            .collect(Collectors.toSet());

            //将都是blow以下的用户放到newUserList中(可以发邮件的用户)
            /*List<String> newUserList = userList.stream()
            .filter(Objects::nonNull) //过滤null的UserAccessReview对象
            .map(UserAccessReview::getItCodeOfUser) //提取it_code_of_user字段
            .filter(Objects::nonNull) //过滤null的用户ID
            .map(String::trim)        //去除首尾空格
            .filter(itCodeUser -> !itCodeUser.isEmpty()) //过滤空字符串（""）
            .map(String::toLowerCase) //转小写
            .filter(itCodeUser -> !uppNatureUserSet.contains(itCodeUser)) //小写匹配排除
            .map(itCodeUser -> itCodeUser + "@lenovo.com") //拼接域名后缀
            .collect(Collectors.toList());*/

            List<String> newUserList = userList.stream()
            .filter(Objects::nonNull)
            .filter(user ->
            {
                //用户的line mgr是SVP及以上的，这批用户的通知先block住
                String status = user.getLineManagerLevelCode();
                if("4".equals(status))
                {
                    return false;
                }
                String userItCode = user.getItCodeOfUser();
                if (userItCode == null) return false;
                userItCode = userItCode.trim().toLowerCase();
                return !userItCode.isEmpty() && !uppNatureUserSet.contains(userItCode); //先判断对象里的 itCode 是否需要排除
            })
            .map(UserAccessReview::getEmail)
            .filter(Objects::nonNull)
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(email -> !email.isEmpty())
            .distinct()
            .collect(Collectors.toList());

            if(CollectionUtil.isEmpty(newUserList))
            {
                log.error("查询final_review_decision最终审核是空的用户数据:没有查询到blow以下的用户");
                return List.of();
            }
            return newUserList;
        }
        catch (Exception e)
        {
            log.error("查询final_review_decision最终审核是空的用户数据出现异常:{}",e.getMessage(),e);
            SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
            sendEmailActionLog.setItCode(itCode);
            sendEmailActionLog.setOperation(sendFlag + "-UAR管理:UAR发送用户邮件:查询final_review_decision最终审核是空的用户数据");
            sendEmailActionLog.setSendFlag(sendFlag);
            sendEmailActionLog.setMessage(e.getMessage());
            sendEmailActionLog.setStackTrace(Arrays.toString(e.getStackTrace()));
            sendEmailActionLog.setBatchNo("暂无");
            sendEmailActionLog.setCreateDate(LocalDateTime.now());
            sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
            return List.of();
        }
    }


    /**
     * @Description TODO 用户发邮件--查询lm或者bpo的审核结果是remove的用户数据
     * @param [useAccessReviewBean, itCode, sendFlag, roleFlag]
     * @author wangfenglong
     * @date 2026/6/25 14:30
    **/
    @Override
    public List<String> getUserByReviewResultIsRemove(UseAccessReviewBean useAccessReviewBean,String itCode,String sendFlag,String roleFlag)
    {
        try
        {
            String flag = "";
            //List<String> uppNatureUserList = userAccessReviewMapper.getUserByBlow(flag);
            List<String> svpList = this.getUserBySVPBandFlag("1");
            List<UserAccessReview> userList = userAccessReviewMapper.selectUserByReviewResultIsRemove(useAccessReviewBean,roleFlag);
            if(CollectionUtil.isEmpty(userList) || CollectionUtil.isEmpty(svpList))
            {
                log.error("查询review_decision是remove的用户数据:没有查询到信息");
                return List.of();
            }

            Set<String> uppNatureUserSet = svpList.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

            List<String> newUserList = userList.stream()
            .filter(Objects::nonNull)
            .filter(user ->
            {
                //用户的line mgr是SVP及以上的，这批用户的通知先block住
                String status = user.getLineManagerLevelCode();
                if("4".equals(status))
                {
                    return false;
                }
                String userItCode = user.getItCodeOfUser();
                if (userItCode == null) return false;
                userItCode = userItCode.trim().toLowerCase();
                return !userItCode.isEmpty() && !uppNatureUserSet.contains(userItCode); //先判断对象里的 itCode 是否需要排除
            })
            .map(UserAccessReview::getEmail)
            .filter(Objects::nonNull)
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(email -> !email.isEmpty())
            .distinct()
            .collect(Collectors.toList());

            if(CollectionUtil.isEmpty(newUserList))
            {
                log.error("查询review_decision是remove的用户数据:没有查询到blow以下的用户");
                return List.of();
            }
            return newUserList;
        }
        catch (Exception e)
        {
            log.error("查询review_decision是remove的用户数据出现异常:{}",e.getMessage(),e);
            SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
            sendEmailActionLog.setItCode(itCode);
            sendEmailActionLog.setOperation(sendFlag + "-UAR管理:UAR发送用户邮件:查询review_decision是remove的用户数据");
            sendEmailActionLog.setSendFlag(sendFlag);
            sendEmailActionLog.setMessage(e.getMessage());
            sendEmailActionLog.setStackTrace(Arrays.toString(e.getStackTrace()));
            sendEmailActionLog.setBatchNo("暂无");
            sendEmailActionLog.setCreateDate(LocalDateTime.now());
            sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
            return List.of();
        }
    }

    /**
     * @Description TODO 用户发邮件--写入到allow_send_email_for_result_remove_user表
     * @param [newUserList, itCode, sendFlag]
     * @author wangfenglong
     * @date 2026/6/25 15:15
     **/
    @Async
    @Override
    public void saveUserToAllowSendEmailForResultRemoveUser(List<String> newUserList,String itCode,String sendFlag)
    {
        try
        {
            int batchSize = 500;
            int totalBpo = newUserList.size();
            userAccessReviewMapper.truncateTableAllowSendEmailForResultRemoveUser();
            for(int i = 0; i < totalBpo; i += batchSize)
            {
                List<String> batchList = newUserList.subList(i, Math.min(i + batchSize, totalBpo));
                int a = userAccessReviewMapper.batchInsertReviewRemoveUser(batchList);
            }
            log.info("审核是remove的用户数据写入到数据库:成功插入 {} 条数据到allow_send_email_for_result_remove_user表", totalBpo);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            log.error("审核是remove的用户数据写入到数据库出现异常:{}",e.getMessage(),e);
            SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
            sendEmailActionLog.setItCode(itCode);
            sendEmailActionLog.setOperation(sendFlag + "审核是remove的用户数据写入到数据库：写入到allow_send_email_for_result_remove_user表");
            sendEmailActionLog.setSendFlag(sendFlag);
            sendEmailActionLog.setMessage(e.getMessage());
            sendEmailActionLog.setStackTrace(Arrays.toString(e.getStackTrace()));
            sendEmailActionLog.setBatchNo("暂无");
            sendEmailActionLog.setCreateDate(LocalDateTime.now());
            sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
        }
    }


    /**
     * @Description TODO 用户发邮件--写入到allow_send_email_include_user表
     * @author wangfenglong
     * @date 2026/3/12 16:46
    **/
    @Async
    @Override
    public void saveUserToAllowSendEmailIncludeUser(List<String> newUserList,String itCode,String sendFlag)
    {
        try
        {
            int batchSize = 500;
            int totalBpo = newUserList.size();
            userAccessReviewMapper.truncateTempAllowUserSendTable();
            for(int i = 0; i < totalBpo; i += batchSize)
            {
                List<String> batchList = newUserList.subList(i, Math.min(i + batchSize, totalBpo));
                int a = userAccessReviewMapper.batchInsertAllowUser(batchList);
            }
            log.info("写入用户邮件给领导看：批量写入用户邮件::成功插入 {} 条数据到allow_send_email_include_user表", totalBpo);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            log.error("获取用户数据出现异常:{}",e.getMessage(),e);
            SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
            sendEmailActionLog.setItCode(itCode);
            sendEmailActionLog.setOperation(sendFlag + "-UAR管理:UAR发送用户邮件");
            sendEmailActionLog.setSendFlag(sendFlag);
            sendEmailActionLog.setMessage(e.getMessage());
            sendEmailActionLog.setStackTrace(Arrays.toString(e.getStackTrace()));
            sendEmailActionLog.setBatchNo("暂无");
            sendEmailActionLog.setCreateDate(LocalDateTime.now());
            sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
        }
    }

    /**
     * @Description TODO 用户发邮件--从allow_send_email_include_user查询出用户信息
     * @author wangfenglong
     * @date 2026/3/13 09:59
    **/
    @Override
    public List<String>  getAllowSendEmailIncludeUserData(UseAccessReviewBean useAccessReviewBean)
    {
        return userAccessReviewMapper.getUserEmailFromAllowSendEmailInclude(useAccessReviewBean);
    }

    /**
     * @Description TODO 发送【最终审核是空的用户】邮件--发送邮件服务（不增加限流，全部推送到队列）
     * @author wangfenglong
     * @date 2026/3/13 10:10
    **/
    @Override
    public CompletableFuture<List<BatchSendResult>> batchSendUserEmailsAsync(List<PreSendBean> resultList, String itCode, String sendFlag)
    {
        List<BatchSendResult> results = Collections.synchronizedList(new ArrayList<>());

        String batchNo = generateBatchNo(itCode,sendFlag);
        String tagModel = "1";

        String toSomeone ="ToUser";
        UarMailTemplate userTemplate = uarMailTemplateService.findTemplateWithMaxVersion(tagModel, toSomeone);
        if(userTemplate == null)
        {
            log.error("{}---toSomeone={},No corresponding template was found.(未找到对应模板)", sendFlag,toSomeone);
            return CompletableFuture.failedFuture(new RuntimeException("SendUserEmail--ToUser未找到对应模板"));
        }

        //替换模版变量
        Map<String, String> variables = new HashMap<>();

        try
        {

            for (PreSendBean record : resultList)
            {
                sendMailTask.sendReminderEmailToRecipientUser(record, batchNo, userTemplate, sendFlag);
            }

            return CompletableFuture.completedFuture(results);
        }
        catch (Exception e)
        {
            log.error("{}-批量发送用户邮件失败【批次号：{}】，异步任务执行过程中抛出异常:", sendFlag,batchNo, e);
            log.error("{}-The batch user email sending failed [Batch ID: {}], and an exception occurred during the execution of the asynchronous task:", sendFlag,batchNo, e);
            SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
            sendEmailActionLog.setItCode(itCode);
            sendEmailActionLog.setOperation(sendFlag + "-UAR管理:UAR发送用户邮件：整体出现异常");
            sendEmailActionLog.setSendFlag(sendFlag);
            sendEmailActionLog.setMessage(e.getMessage());
            sendEmailActionLog.setStackTrace(Arrays.toString(e.getStackTrace()));
            sendEmailActionLog.setBatchNo(batchNo);
            sendEmailActionLog.setCreateDate(LocalDateTime.now());
            sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * @Description TODO 给【最终审核是移除的用户】发邮件--发送邮件服务（不增加限流，全部推送到队列）
     * @author wangfenglong
     * @date 2026/5/9 16:27
    **/
    @Override
    public CompletableFuture<List<BatchSendResult>> batchSendFinalRemoveUserEmailsAsync(
            Map<String, byte[]> excelMap, List<PreSendBean> resultList, String itCode, String sendFlag)
    {
        List<BatchSendResult> results = Collections.synchronizedList(new ArrayList<>());
        String batchNo = generateBatchNo(itCode, sendFlag);
        UarMailTemplate userTemplate = uarMailTemplateService.findTemplateWithMaxVersion("2", "ToUser");
        if (userTemplate == null)
        {
            log.error("{}---toSomeone=ToUser,No corresponding template was found.(未找到对应模板)", sendFlag);
            return CompletableFuture.failedFuture(new RuntimeException("SendFinalRemoveUserEmail--ToUser未找到对应模板"));
        }

        for (PreSendBean record : resultList)
        {
            String recipientItCode = record.getTo();
            byte[] excelBytes = excelMap.get(recipientItCode);
            sendMailTask.sendFinalRemoveEmailToRecipientUser(record, excelBytes, batchNo, userTemplate, sendFlag);
        }
        return CompletableFuture.completedFuture(results);
    }



    /**
     * @Description TODO 发送[审核结果是remove的用户]邮件--【或者bpo的审核结果是remove的用户数据】-（不增加限流，全部推送到队列）
     * @param [request, resultList, itCode, sendFlag] 敢爱敢恨 林子祥
     * @author wangfenglong
     * @date 2026/6/26 11:17
    **/
    @Override
    public CompletableFuture<List<BatchSendResult>> batchSendReviewRemoveUserEmailsAsync(List<PreSendBean> resultList, String itCode, String sendFlag)
    {
        List<BatchSendResult> results = Collections.synchronizedList(new ArrayList<>());

        String batchNo = generateBatchNo(itCode,sendFlag);
        String tagModel = "3";

        String toSomeone ="ToUser";
        UarMailTemplate userTemplate = uarMailTemplateService.findTemplateWithMaxVersion(tagModel, toSomeone);
        if(userTemplate == null)
        {
            log.error("{}---toSomeone={},No corresponding template was found.(未找到对应模板)", sendFlag, toSomeone);
            return CompletableFuture.failedFuture(new RuntimeException("SendUserEmail--ToUser:No corresponding template was found"));
        }

        try
        {
            for (PreSendBean record : resultList)
            {
                sendMailTask.sendRemoveRoleEmailToRecipientUser(record, batchNo, userTemplate, sendFlag);
            }

            return CompletableFuture.completedFuture(results);
        }
        catch (Exception e)
        {
            log.error("{}-发送[审核结果是remove的用户]邮件失败【批次号：{}】，异步任务执行过程中抛出异常:", sendFlag,batchNo, e);
            log.error("{}-The batch user email sending failed [Batch ID: {}], and an exception occurred during the execution of the asynchronous task:", sendFlag,batchNo, e);
            SendEmailActionLog sendEmailActionLog = new SendEmailActionLog();
            sendEmailActionLog.setItCode(itCode);
            sendEmailActionLog.setOperation(sendFlag + "-UAR管理:发送[审核结果是remove的用户]邮件：整体出现异常");
            sendEmailActionLog.setSendFlag(sendFlag);
            sendEmailActionLog.setMessage(e.getMessage());
            sendEmailActionLog.setStackTrace(Arrays.toString(e.getStackTrace()));
            sendEmailActionLog.setBatchNo(batchNo);
            sendEmailActionLog.setCreateDate(LocalDateTime.now());
            sendEmailActionLogService.addSendEmailLog(sendEmailActionLog);
            return CompletableFuture.failedFuture(e);
        }
    }




    /**
     * @Description TODO 给所有lm和bpo发邮件
     * @author wangfenglong
     * @date 2026/2/3 15:42
     **/
    @Async
    @Override
    public void sendAllEmail(List<AutoMailSendLineManagerTemp> linaManagerSendDataList, List<AutoMailSendBpoTemp> bpoSendDataList, String batchNo, String itCode, String nodeId)
    {
        List<Future<?>> futureList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(linaManagerSendDataList))
        {
            this.sendLmMailsAsync(linaManagerSendDataList, batchNo, itCode, nodeId, futureList);
        }
        if (!CollectionUtils.isEmpty(bpoSendDataList))
        {
            this.sendBpoMailsAsync(bpoSendDataList, batchNo, itCode, nodeId, futureList);
        }
        //等待所有异步任务完成后，再打印结束日志（避免日志失真）
        this.waitForAllTasksComplete(futureList, batchNo, nodeId);
        log.info("【给所有lm和bpo发送邮件】批次号：{}，节点{}所有任务提交完成，异步执行结束", batchNo, nodeId);
    }




    //～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～//
    //～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～～//
    /**
     * @Description TODO 后台定时任务发送邮件_获取需要发送的邮件的数据
     * @author wangfenglong
     * @date 2025/12/26 12:14
    **/
    @Override
    public List<AutoMailSendLineManagerTemp> getLinaManagerSendData()
    {
        try
        {
            autoMailSendLineManagerTempMapper.truncateTempTable();// 清空临时表
            List<String> svpList1 = autoMailSendLineManagerTempMapper.getSVPEmailBySVPBandFlag("1");;//查询出高管
            log.info("定时任务批量发送邮件:临时表清空成功");
            //查询lineManager数据
            List<AutoMailSendLineManagerTemp> linaManagerSendDataList = autoMailSendLineManagerTempMapper.getLinaManagerSendData();
            //去重
            List<AutoMailSendLineManagerTemp> distinctLMList = linaManagerSendDataList.stream()
            .filter(temp -> temp.getToEmail() != null)
            .collect(Collectors.groupingBy(AutoMailSendLineManagerTemp::getToEmail))
            .values()
            .stream()
            .map(group -> group.get(0))
            .collect(Collectors.toList());
            log.info("定时任务批量发送邮件lineManager：SVP过滤前{}条", distinctLMList.size());
            //过滤高管
            if (CollectionUtil.isNotEmpty(svpList1))
            {
                Set<String> svpList = new HashSet<>(svpList1);
                distinctLMList = distinctLMList.stream().filter(temp -> !(svpList.contains(temp.getToEmail()))).collect(Collectors.toList());
            }
            log.info("定时任务批量发送邮件lineManager：SVP过滤后{}条", distinctLMList.size());
            int batchSize = 500;
            int total = distinctLMList.size();
            log.info("定时任务批量发送邮件:LineManager邮件总数量: {} 条数据",total);
            //插入
            for(int i = 0; i < total; i += batchSize)
            {
                List<AutoMailSendLineManagerTemp> batchList = distinctLMList.subList(i, Math.min(i + batchSize, total));
                autoMailSendLineManagerTempMapper.batchInsert(batchList);
            }
            log.info("定时任务批量发送邮件:成功插入LineManager {} 条数据到AutoMailSendLineManagerTemp表", total);

            //查询bpo数据
            List<AutoMailSendBpoTemp> bpoSendDataList = autoMailSendLineManagerTempMapper.getBpoSendData();
            //步骤1：筛选出item包含';'的元素，存入新集合
            List<AutoMailSendBpoTemp> newList = bpoSendDataList.stream()
            .filter(data -> data.getBpoBandEdFlag() != null && data.getBpoBandEdFlag().contains(";"))
            .collect(Collectors.toList());
            //步骤2：从原集合bpoSendDataList中批量删除新集合中的所有元素
            //newList 存放所有包含';'的元素，bpoSendDataList 已移除这些元素
            bpoSendDataList.removeAll(newList);
            //开始处理包含;的数据
            Map<String,String>BpoMap = new HashMap<>();
            for(AutoMailSendBpoTemp bpoTemp : newList)
            {
                try
                {
                    //BpoMap = StringUtils.getebaySendMailToBpo(bpoTemp);
                    BpoMap = StringUtils.getebaySendMailToBpoNotCheckBpoBandEdFlag(bpoTemp);
                    if(BpoMap != null && !BpoMap.isEmpty())
                    {
                        for(Map.Entry<String, String> entry : BpoMap.entrySet())
                        {
                            AutoMailSendBpoTemp spliteBpo = new AutoMailSendBpoTemp();
                            spliteBpo.setAppNameNumber(bpoTemp.getAppNameNumber());
                            spliteBpo.setAppNameList(bpoTemp.getAppNameList());
                            spliteBpo.setBpo(entry.getKey());
                            spliteBpo.setBpoEmail(entry.getValue());
                            spliteBpo.setToEmail(entry.getValue());
                            spliteBpo.setItCodeUserNumber(bpoTemp.getItCodeUserNumber());
                            spliteBpo.setItCodeUserNameList(bpoTemp.getItCodeUserNameList());
                            spliteBpo.setCmdbId(bpoTemp.getCmdbId());
                            spliteBpo.setBpoBandEdFlag("split");
                            bpoSendDataList.add(spliteBpo);
                        }
                    }
                }
                catch (Exception e)
                {
                    log.info("定时任务批量发送邮件:发送邮件实际逻辑:校验BPO是否多个邮箱失败:" , e);
                    bpoTemp.setBpoBandEdFlag("Error");
                    bpoSendDataList.add(bpoTemp);
                }
            }

            //去重
            List<AutoMailSendBpoTemp> distinctBpoList = bpoSendDataList.stream()
            .filter(temp -> temp.getToEmail() != null)
            .collect(Collectors.groupingBy(AutoMailSendBpoTemp::getToEmail))
            .values()
            .stream()
            .map(group -> group.get(0))
            .collect(Collectors.toList());
            log.info("定时任务批量发送邮件bpo：SVP过滤前{}条", distinctBpoList.size());
            //过滤高管
            if (CollectionUtil.isNotEmpty(svpList1))
            {
                Set<String> svpList = new HashSet<>(svpList1);
                distinctBpoList = distinctBpoList.stream().filter(temp -> !(svpList.contains(temp.getToEmail()))).collect(Collectors.toList());
            }
            log.info("定时任务批量发送邮件bpo：SVP过滤后{}条", distinctBpoList.size());
            int totalBpo = distinctBpoList.size();
            log.info("定时任务批量发送邮件:BPO邮件总数量: {} 条数据",totalBpo);
            //插入
            for(int i = 0; i < totalBpo; i += batchSize)
            {
                List<AutoMailSendBpoTemp> batchList = distinctBpoList.subList(i, Math.min(i + batchSize, totalBpo));
                autoMailSendLineManagerTempMapper.batchInsertBpo(batchList);
            }
            log.info("定时任务批量发送邮件:成功插入BPO {} 条数据到AutoMailSendBpoTemp表", totalBpo);
            return List.of();
        }
        catch (Exception e)
        {
            log.error("定时任务批量发送邮件:填充临时表时发生错误", e);
            throw new RuntimeException("定时任务批量发送邮件:填充临时表失败: " + e.getMessage(),e);
        }
    }

    /**
     * @Description TODO 后台定时任务发送邮件_定时任务要发邮件了，快闪开
     * @author wangfenglong
     * @date 2025/12/26 15:03
    **/
    @Override
    public <T> boolean sendMail(T mailData,String toSomeone,String finalBatchNo,String itCode)
    {
        String toEmail = "";
        Long toId = 1L;
        String lineManager = "";
        String bpo = "";
        // 若需要操作mailData的具体字段，需先进行类型判断和强制转换（因无泛型边界限制，编译器无法推断具体类型）
        if (mailData instanceof AutoMailSendLineManagerTemp)
        {
            AutoMailSendLineManagerTemp lineManagerTemp = (AutoMailSendLineManagerTemp) mailData;
            toEmail = lineManagerTemp.getToEmail();
            toId = lineManagerTemp.getId();
            lineManager = lineManagerTemp.getLineManager();
        }
        else if (mailData instanceof AutoMailSendBpoTemp)
        {
            AutoMailSendBpoTemp bpoTemp = (AutoMailSendBpoTemp) mailData;
            toEmail = bpoTemp.getToEmail();
            toId = bpoTemp.getId();
            bpo = bpoTemp.getBpo();
        }
        else
        {
            // 处理未知类型，可抛出异常或返回false
            throw new IllegalArgumentException("不支持的mailData类型：" + (mailData == null ? "null" : mailData.getClass().getName()));
        }

        try
        {
            //获取模板
            UarMailTemplate template = uarMailTemplateService.findTemplateWithMaxVersion("1", toSomeone);
            if(template == null)
            {
                throw new RuntimeException("未找到对应模板: tag=" + "1" + ", toSomeone=" + toSomeone);
            }

            Map<String, String> variables = new HashMap<>();
            String uarAppOwner = itCode;
            UseAccessReviewBean bean = new UseAccessReviewBean();
            bean.setItCodeOfUser(itCode);
            List<ItsApplicationData> uarProcesserList = itsApplicationDataMapper.getApplicationDataByUARProcesser(bean);
            if(CollectionUtil.isNotEmpty(uarProcesserList))
            {
                Optional<String> uarProcesserMail = uarProcesserList.stream()
                        .filter(Objects::nonNull)
                        .map(ItsApplicationData::getUarProcessorEmail)
                        .filter(mail -> mail != null && !mail.isEmpty())
                        .findFirst();
                uarAppOwner = uarProcesserMail.orElse(itCode);
            }

            // 准备模板变量
            if("ToLineManager".equals(toSomeone))
            {
                variables.put("{itCode}", lineManager);
                variables.put("{uarProcessor}", uarAppOwner);
            }
            else if("ToBPO".equals(toSomeone))
            {
                variables.put("{itCode}", bpo);
                variables.put("{uarProcessor}", uarAppOwner);
            }
            else
            {
                variables.put("{itCode}", "你是谁?");
                variables.put("{uarProcessor}", uarAppOwner);
            }

            try
            {
                // 发送邮件
                uarMailService.sendTemplatedEmail(template.getId(), Collections.singleton(toEmail.trim()), null, variables, finalBatchNo);
                return true;
            }
            catch (Exception e)
            {
                log.error("定时任务批量发送邮件-发送邮件开始：邮件发送失败: email={},原因：{}",toEmail,e.getMessage(),e);
                return false;
            }
        }
        catch (Exception e)
        {
            log.error("发送邮件失败，邮件ID：{},email={}", toId,toEmail, e);
            return false;
        }
    }

    /**
     * @Description TODO 后台定时任务发送邮件_定时任务发送邮件生成批次号
     * @author wangfenglong
     * @date 2025/12/31 17:23
    **/
    @Override
    public String generateBatchNoByScheduledTask()
    {
        int randomNum = 10000 + ThreadLocalRandom.current().nextInt(90000);
        return String.format("%s_%s_%s_%d", "ScheduledTask", System.currentTimeMillis(), nodeIdUtil.getNodeId(), randomNum);
    }

    /**
     * @Description TODO 后台定时任务发送邮件_原子化抢占LM邮件并标记为发送中（同一个事务）
     * @author wangfenglong
     * @date 2025/12/31 16:53
    **/
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public List<AutoMailSendLineManagerTemp> preemptAndMarkLmMails(int batchSize, String nodeId)
    {
        // 步骤1：抢占数据（加行锁，事务持有锁，不释放）
        List<AutoMailSendLineManagerTemp> preemptedMails = autoMailSendLineManagerTempMapper.preemptMails(batchSize);
        if (CollectionUtils.isEmpty(preemptedMails))
        {
            return new ArrayList<>();
        }
        // 步骤2：批量标记为发送中（同一个事务内，行锁未释放）
        preemptedMails.forEach(mail ->
        {
            // 此处可校验标记结果，失败则抛出异常回滚事务
            int affectRows = autoMailSendLineManagerTempMapper.markSending(mail.getId(), nodeId);
            if (affectRows == 0)
            {
                throw new RuntimeException("LM邮件标记发送中失败，邮件ID：" + mail.getId());
            }
        });
        // 事务提交后，行锁释放，此时其他节点无法抢占已标记的数据
        return preemptedMails;
    }

    /**
     * @Description TODO 后台定时任务发送邮件_原子化抢占BPO邮件并标记为发送中（同一个事务）
     * @author wangfenglong
     * @date 2025/12/31 16:54
    **/
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public List<AutoMailSendBpoTemp> preemptAndMarkBpoMails(int batchSize, String nodeId)
    {
        List<AutoMailSendBpoTemp> preemptedMails = autoMailSendLineManagerTempMapper.preemptMailsToBpo(batchSize);
        if (CollectionUtils.isEmpty(preemptedMails))
        {
            return new ArrayList<>();
        }
        preemptedMails.forEach(mail ->
        {
            int affectRows = autoMailSendLineManagerTempMapper.markSendingToBpo(mail.getId(), nodeId);
            if (affectRows == 0) {
                throw new RuntimeException("BPO邮件标记发送中失败，邮件ID：" + mail.getId());
            }
        });
        return preemptedMails;
    }

    /**
     * @Description TODO 后台定时任务发送邮件_等待所有异步任务完成
     * @author wangfenglong
     * @date 2025/12/31 16:56
    **/
    @Override
    public void waitForAllTasksComplete(List<Future<?>> futureList, String batchNo, String nodeId)
    {
        if (CollectionUtils.isEmpty(futureList))
        {
            log.info("【邮件任务等待】批次号：{}，节点{}无异步任务需要等待", batchNo, nodeId);
            return;
        }
        for (Future<?> future : futureList)
        {
            try
            {
                // 等待任务完成，无超时限制（可根据业务配置超时时间）
                future.get();
            }
            catch (Exception e)
            {
                log.error("【邮件任务等待】批次号：{}，节点{}异步任务执行异常", batchNo, nodeId, e);
            }
        }
        log.info("【邮件任务等待】批次号：{}，节点{}所有异步任务执行完成", batchNo, nodeId);
    }

    /**
     * @Description TODO 后台定时任务发送邮件_异步发送BPO邮件
     * @author wangfenglong
     * @date 2025/12/31 17:00
    **/
    @Override
    public void sendBpoMailsAsync(List<AutoMailSendBpoTemp> bpoMails, String batchNo, String itCode, String nodeId, List<Future<?>> futureList)
    {
        for (AutoMailSendBpoTemp mail : bpoMails)
        {
            AutoMailSendBpoTemp currentMail = mail;
            Future<?> future = emailSendExecutorSchedule.submit(() ->
            {
                try
                {
                    // 修复：传入正确的接收人类型ToBPO
                    boolean sendSuccess = this.sendMail(currentMail, "ToBPO", batchNo, itCode);
                    int newStatus = sendSuccess ? 2 : 3;
                    int newRetryCount = sendSuccess ? currentMail.getRetryCount() : currentMail.getRetryCount() + 1;
                    autoMailSendLineManagerTempMapper.updateMailStatusToBpo(currentMail.getId(), newStatus, newRetryCount);
                    log.info("【BPO邮件发送】批次号：{}，节点{}，邮件ID{},执行线程：{},发送{}", batchNo, nodeId, currentMail.getId(), Thread.currentThread().getName(),sendSuccess ? "成功" : "失败");
                }
                catch (Exception e)
                {
                    log.error("【BPO邮件发送】批次号：{}，节点{}，邮件ID{},执行异常", batchNo, nodeId, currentMail.getId(), e);
                    // 异常标记为失败，增加重试次数
                    autoMailSendLineManagerTempMapper.updateMailStatusToBpo(currentMail.getId(), 3, currentMail.getRetryCount() + 1);
                }
            });
            futureList.add(future);
        }
    }

    /**
     * @Description TODO 后台定时任务发送邮件_异步发送LineManager邮件
     * @author wangfenglong
     * @date 2025/12/31 17:01
    **/
    @Override
    public void sendLmMailsAsync(List<AutoMailSendLineManagerTemp> lmMails, String batchNo, String itCode, String nodeId, List<Future<?>> futureList)
    {
        for (AutoMailSendLineManagerTemp mail : lmMails)
        {
            AutoMailSendLineManagerTemp currentMail = mail;
            // 使用submit收集Future，跟踪任务状态
            Future<?> future = emailSendExecutorSchedule.submit(() ->
            {
                try
                {
                    boolean sendSuccess = this.sendMail(currentMail, "ToLineManager", batchNo, itCode);
                    int newStatus = sendSuccess ? 2 : 3;
                    int newRetryCount = sendSuccess ? currentMail.getRetryCount() : currentMail.getRetryCount() + 1;
                    autoMailSendLineManagerTempMapper.updateMailStatus(currentMail.getId(), newStatus, newRetryCount);
                    log.info("【LM邮件发送】批次号：{}，节点{}，邮件ID{}，执行线程：{}，发送{}", batchNo, nodeId, currentMail.getId(), Thread.currentThread().getName(),sendSuccess ? "成功" : "失败");
                }
                catch (Exception e)
                {
                    log.error("【LM邮件发送】批次号：{}，节点{}，邮件ID{},执行异常", batchNo, nodeId, currentMail.getId(), e);
                    // 异常标记为失败，增加重试次数
                    autoMailSendLineManagerTempMapper.updateMailStatus(currentMail.getId(), 3, currentMail.getRetryCount() + 1);
                }
            });
            futureList.add(future);
        }
    }

    /**
     * @Description TODO 后台定时任务发送邮件_重置超时邮件（兜底逻辑）
     * @author wangfenglong
     * @date 2025/12/31 17:08
    **/
    @Override
    public void resetTimeoutMails(String nodeId, String batchNo)
    {
        try
        {
            int lmResetCount = autoMailSendLineManagerTempMapper.resetTimeoutMails(GlobalBusinessStatusEnum.MAIL_TIMEOUT_SECONDS.code);
            int bpoResetCount = autoMailSendLineManagerTempMapper.resetTimeoutMailsToBpo(GlobalBusinessStatusEnum.MAIL_TIMEOUT_SECONDS.code);
            log.info("【邮件超时重置】批次号：{}，节点{}重置LineManager超时邮件{}封，BPO超时邮件{}封", batchNo, nodeId, lmResetCount, bpoResetCount);
        }
        catch (Exception e)
        {
            log.error("【邮件超时重置】批次号：{}，节点{}重置超时邮件异常", batchNo, nodeId, e);
        }
    }
}
