package com.lenovo.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.bean.AdvancedSearchBean;
import com.lenovo.bean.EmailFailedBean;
import com.lenovo.bean.ImportResult;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.config.GlobalBusinessStatusEnum;
import com.lenovo.constant.AccessReviewScope;
import com.lenovo.dto.BatchSendRequest;
import com.lenovo.dto.SendCheckResponse;
import com.lenovo.entity.*;
import com.lenovo.mapper.*;
import com.lenovo.security.exception.BadRequestException;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.security.utils.StringUtils;
import com.lenovo.service.UserAccessReviewService;
import com.lenovo.service.RegionalUarPolicyService;
import com.lenovo.service.UarMailService;
import com.lenovo.service.UarMailTemplateService;
import com.lenovo.service.UseAccessReviewEmailSendService;
import com.lenovo.util.RedisUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ooxml.util.SAXHelper;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@AllArgsConstructor
public class UserAccessReviewServiceImpl extends ServiceImpl<UserAccessReviewMapper, UserAccessReview> implements UserAccessReviewService
{
    private final UarMailTemplateService uarMailTemplateService;
    private final UseAccessReviewEmailSendService emailSendService;
    private final UserAccessReviewMapper userAccessReviewMapper;
    private final ItsApplicationAccessDataServiceMapper itsApplicationAccessDataServiceMapper;
    private final UarCycleMaintenanceMapper uarCycleMaintenanceMapper;
    private final ItsApplicationDataMapper itsApplicationDataMapper;
    private final UserAccessReviewTempMapper userAccessReviewTempMapper;
    private final LenovoUserMapper lenovoUserMapper;
    private final RedisUtils redisUtils;
    private final UserAccessReviewTempMapper tempMapper;
    private final UarMailSendLogMapper uarMailSendLogMapper;
    private final UseAccessReviewEmailSendServiceImpl useAccessReviewEmailSendService;
    private final UarIgnoreRuleMapper uarIgnoreRuleMapper;
    private final RegionalUarPolicyService regionalUarPolicyService;


    /**
     * @Description TODO 发送邮件之后批量更新 overall_send_status=Sent或者Failed
     * @author wangfenglong
     * @date 2025/11/28 16:17
    **/
    @Override
    @Transactional
    public boolean updateOverallSendStatusBatch(List<String> sequenceNumbers, String status,List<String>sequenceNumberList)
    {
        try
        {
            if (sequenceNumbers == null || sequenceNumbers.isEmpty())
            {
                //这是全选的批量
                int affectedRows = 0;
                int batchSize = 500; // 每批处理500条
                for (int i = 0; i < sequenceNumberList.size(); i += batchSize)
                {
                    List<String> subList = sequenceNumberList.subList(i, Math.min(i + batchSize, sequenceNumberList.size()));
                    affectedRows = userAccessReviewMapper.updateOverallSendStatusBatch(subList, status);
                }
                log.info("批量更新邮件发送状态:全选的批量：成功: status={}, 影响行数={}", status, affectedRows);
                return affectedRows > 0;
            }
            else
            {
                //这是单选的批量
                int affectedRows = 0;
                int batchSize = 500;
                for (int i = 0; i < sequenceNumbers.size(); i += batchSize)
                {
                    List<String> subList = sequenceNumbers.subList(i, Math.min(i + batchSize, sequenceNumbers.size()));
                    affectedRows = userAccessReviewMapper.updateOverallSendStatusBatch(subList, status);
                }
                log.info("批量更新邮件发送状态:单选的批量:成功: status={}, 影响行数={}", status, affectedRows);
                return affectedRows > 0;
            }
        }
        catch (Exception e)
        {
            log.error("批量更新邮件发送状态失败: status={}, sequenceNumbers={}", status, sequenceNumbers, e);
            throw new RuntimeException("批量更新邮件发送状态失败: " + e.getMessage());
        }
    }

    /**
     * @Description TODO 发送邮件之后批量更新 current_round=current_round+1
     * @author wangfenglong
     * @date 2026/2/5 13:49
    **/
    @Override
    @Transactional
    public boolean updateCurrentAroundBatch(List<String> sequenceNumbers,UseAccessReviewBean useAccessReviewBean)
    {
        if (sequenceNumbers == null || sequenceNumbers.isEmpty())
        {
            log.warn("序列号列表为空，跳过更新currentAround");
            return true;
        }
        int affectedRows = 0;
        int batchSize = 500; // 每批处理500条
        for (int i = 0; i < sequenceNumbers.size(); i += batchSize)
        {
            List<String> subList = sequenceNumbers.subList(i, Math.min(i + batchSize, sequenceNumbers.size()));
            affectedRows = userAccessReviewTempMapper.updateCurrentRoundBatch(subList);
        }
        //log.info("批量更新邮件发送状态成功: 发一次邮件current_round就+1");
        return affectedRows > 0;
    }

    /**
     * @Description TODO 三个接口(发送中，已发送，发送失败)：overall_send_status的分页查询
     * @author wangfenglong
     * @date 2025/11/27 10:22
    **/
    @Override
    public Page<UserAccessReview> queryBySendStatus(UseAccessReviewBean  useAccessReviewBean,String overallSendStatus, Integer page, Integer size,String roleFlag)
    {
        try
        {
            Page<UserAccessReview> p = new Page<>(page, size);
            //Page<UserAccessReview> result = userAccessReviewMapper.selectBySendStatus(p, overallSendStatus,useAccessReviewBean);
            Page<UserAccessReview> result = userAccessReviewMapper.selectBySendStatusToRewrite(p,overallSendStatus,useAccessReviewBean,roleFlag);
            List<UserAccessReview> reviewList = result.getRecords();

            //空指针防护（列表为null或空时，直接返回结果，避免异常）
            if (org.springframework.util.CollectionUtils.isEmpty(reviewList))
            {
                return result;
            }

            //批量提取所有uuid（去重，减少无效查询）
            List<String> uuidList = reviewList.stream()
            .map(UserAccessReview::getUuid)
            .distinct()
            .collect(Collectors.toList());

            List<String> recipientTypes = Arrays.asList("LineManager", "BPO");
            Map<String, List<UseAccessReviewEmailSend>> emailSendMap = useAccessReviewEmailSendService.getBatchByUuidListAndRecipientTypes(uuidList, recipientTypes);
            reviewList.forEach(userAccessReviewTemp ->
            {
                String tempUuid = userAccessReviewTemp.getUuid();
                //拼接Map Key，获取对应邮件列表（无数据时返回空列表，避免null）
                String lmMapKey = tempUuid + "-" + "LineManager";
                String bpoMapKey = tempUuid + "-" + "BPO";
                List<UseAccessReviewEmailSend> lmEmailList = emailSendMap.getOrDefault(lmMapKey, new ArrayList<>());
                List<UseAccessReviewEmailSend> bpoEmailList = emailSendMap.getOrDefault(bpoMapKey, new ArrayList<>());
                //赋值邮件列表
                userAccessReviewTemp.setLineManagerEmailStatus(lmEmailList);
                userAccessReviewTemp.setBpoEmailStatus(bpoEmailList);
            });
            return result;

            /*for(UserAccessReview userAccessReview : reviewList)
            {
                String tempUuid = userAccessReview.getUuid();
                List<UseAccessReviewEmailSend> lineManagerEmailSendList = emailSendService.getByReviewUuidAndRecipientType(tempUuid,"LineManager");
                List<UseAccessReviewEmailSend> bpoEmailSendList = emailSendService.getByReviewUuidAndRecipientType(tempUuid,"BPO");
                userAccessReview.setLineManagerEmailStatus(lineManagerEmailSendList);
                userAccessReview.setBpoEmailStatus(bpoEmailSendList);
            }*/
        }
        catch (Exception e)
        {
            log.error("三个接口(发送中，已发送，发送失败):查询异常: status={}", overallSendStatus, e);
            throw new RuntimeException("三个接口(发送中，已发送，发送失败):查询异常" + e.getMessage());
        }
    }

    /**
     * @Description TODO (发送中，已发送，发送失败)overall_send_status的情况下获取去重应用名称列表
     * @author wangfenglong
     * @date 2025/11/27 12:30
    **/
    @Override
    public List<AdvancedSearchBean> getDistinctApplicationBySendStatus(String roleFlag,String overallSendStatus,UseAccessReviewBean  useAccessReviewBean)
    {
        try
        {
            return userAccessReviewMapper.selectDistinctApplicationBySendStatus(roleFlag,overallSendStatus,useAccessReviewBean);
        }
        catch (Exception e)
        {
            log.error("发送中，已发送，发送失败)获取去重应用名称：异常: status={}", overallSendStatus, e);
            throw new RuntimeException("发送中，已发送，发送失败)获取去重应用名称：异常：" + e.getMessage());
        }
    }

    /**
     * @Description TODO 获取当前账户角色
     * @author wangfenglong
     * @date 2026/4/23 15:43
    **/
    @Override
    public EmailFailedBean getCurrentUserRole()
    {
        EmailFailedBean bean = new EmailFailedBean();
        String itCode = SecurityUtils.getCurrentUserId();
        Role roleContainList = (Role)redisUtils.hget(itCode+ GlobalBusinessStatusEnum.REDIS_KEY_USER_ROLE.desc,itCode);
        if(Objects.isNull(roleContainList) || CollectionUtil.isEmpty(roleContainList.getRoleList()))
        {
            bean.setRoleFlag("1");
            return bean;
        }

        List<String> SysITRoleList = List.of("View_Only_IT","UAR_Admin_IT","UAR_System_Admin_IT");//IT权限
        List<String> SysUarRoleList = List.of("UAR_Processer");//UAR权限
        List<String> uniqueRoleList = roleContainList.getRoleList().stream().map(Role::getName).distinct().collect(Collectors.toList());

        //包含IT角色
        if(uniqueRoleList.stream().anyMatch(SysITRoleList::contains))
        {
            bean.setRoleFlag("2");
            return bean;
        }

        //包含UAR角色
        if(uniqueRoleList.stream().anyMatch(SysUarRoleList::contains))
        {
            Optional<Role> uarRoleOpt = roleContainList.getRoleList().stream().filter(role -> GlobalBusinessStatusEnum.UAR_PROCESSER.desc.equals(role.getName())).findFirst();//获取name=UAR_Processer的对象
            Role uarRole = uarRoleOpt.orElse(null); // 无匹配时返回null
            if(Objects.nonNull(uarRole))
            {
                //包含UAR_Processer权限，但是没有cmdbID。有权限但是看不到数据
                if(null == uarRole.getCmdbIdOfUarProcesser() || com.lenovo.security.utils.StringUtils.isEmpty(uarRole.getCmdbIdOfUarProcesser()))
                {
                    bean.setRoleFlag("1");
                    return bean;
                }
                List<String> cmdbIdList = Stream.of(uarRole.getCmdbIdOfUarProcesser().split(",")).collect(Collectors.toList());
                bean.setRoleFlag("3");
                bean.setCmdbIdList(cmdbIdList);
                return bean;
            }
            log.error("发送失败页面校验:获取当前登陆账户角uarRoleOpt=null");
            bean.setRoleFlag("1");
            return bean;
        }
        bean.setRoleFlag("1");
        return bean;
    }

    /**
     * @Description TODO 取当前登陆账户选择的过滤条件
     * @author wangfenglong
     * @date 2026/4/23 15:48
    **/
    @Override
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
     * @Description TODO 根据筛选条件获取选中的数据
     * @author wangfenglong
     * @date 2026/4/23 15:54
    **/
    @Override
    public List<UserAccessReview> getSelectedRecords(BatchSendRequest request,String successFailedFlag)
    {
        List<UserAccessReview> selectedRecords;
        EmailFailedBean roleBean = this.getCurrentUserRole();
        if (request.getIsALL())
        {
            UseAccessReviewBean bean = this.getCurrentRequestFilters(request);
            if("3".equals(roleBean.getRoleFlag()))
            {
                bean.setCmdbIdList(roleBean.getCmdbIdList());
            }
            selectedRecords = userAccessReviewMapper.getAllNeeDToSendEmailFromUserAccessReviewNew(bean,roleBean.getRoleFlag(),successFailedFlag);
        }
        else
        {
            if(request.getSequenceNumbers() == null || request.getSequenceNumbers().isEmpty())
            {
                return List.of();
            }
            selectedRecords = userAccessReviewMapper.getAllNeeDToSendEmailFromUserAccessReviewBySequenceNumbers(request.getSequenceNumbers());
        }
        if(CollectionUtil.isEmpty(selectedRecords))
        {
            return List.of();
        }
        for(UserAccessReview userAccessReviewTemp : selectedRecords)
        {
            if(null == userAccessReviewTemp.getLineManagerEmail() || userAccessReviewTemp.getLineManagerEmail().trim().isEmpty())
            {
                //userAccessReviewTemp.setLineManagerEmail(userAccessReviewTemp.getLineManager() + "@lenovo.com");
                if(StringUtils.isNotBlank(userAccessReviewTemp.getLineManager()))
                {
                    List<String> userEmailList = userAccessReviewMapper.getUserEmailByUserName(userAccessReviewTemp.getLineManager());
                    if(!userEmailList.isEmpty() && StringUtils.isNotBlank(userEmailList.get(0)))
                    {
                        userAccessReviewTemp.setLineManagerEmail(userEmailList.get(0));
                    }
                }
            }
            if(null == userAccessReviewTemp.getBpoEmail() || userAccessReviewTemp.getBpoEmail().trim().isEmpty())
            {
                //userAccessReviewTemp.setBpoEmail(userAccessReviewTemp.getBpo() + "@lenovo.com");
                if(StringUtils.isNotBlank(userAccessReviewTemp.getBpo()))
                {
                    List<String> userEmailList = userAccessReviewMapper.getUserEmailByUserName(userAccessReviewTemp.getBpo());
                    if(!userEmailList.isEmpty() && StringUtils.isNotBlank(userEmailList.get(0)))
                    {
                        userAccessReviewTemp.setBpoEmail(userEmailList.get(0));
                    }
                }
            }
        }
        return selectedRecords;
    }

    /**
     * @Description TODO 发送邮件统计各种发送状态的邮件数量
     * @author wangfenglong
     * @date 2026/4/24 10:54
    **/
    @Override
    public int getSendEmailTotalCount(String roleFlag, String overallSendStatus,UseAccessReviewBean bean)
    {
        if("Failed".equals(overallSendStatus))
        {
            QueryWrapper<UarMailSendLog> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("delegate","delegatee");
            queryWrapper.eq("status","FAILED");
            //int delegateInt =  uarMailSendLogMapper.selectCount(queryWrapper).intValue();
            List<UarMailSendLog> delegateList = uarMailSendLogMapper.selectList(queryWrapper);
            if(CollectionUtil.isNotEmpty(delegateList))
            {
                long distinctEmailCount = delegateList.stream()
                .filter(log -> log != null && StringUtils.isNotBlank(log.getRecipientEmails()))
                .map(log -> log.getRecipientEmails().trim())
                .distinct()
                .count();
                int delegateInt = Math.toIntExact(distinctEmailCount);
                return userAccessReviewMapper.getSendEmailTotalCount(roleFlag,overallSendStatus,bean) + delegateInt;
            }
        }
        return userAccessReviewMapper.getSendEmailTotalCount(roleFlag,overallSendStatus,bean) ;
    }

    /**
     * @Description TODO 发送通知页面--更新待发送列表(刷新数据接口)
     * @author wangfenglong
     * @date 2026/4/24 15:52
    **/
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean refreshData()
    {
        try
        {
            EmailFailedBean roleBean = this.getCurrentUserRole();
            String roleFlag = roleBean.getRoleFlag();
            UseAccessReviewBean bean = new UseAccessReviewBean();
            if("3".equals(roleFlag))
            {
                bean.setCmdbIdList(roleBean.getCmdbIdList());
            }
            userAccessReviewMapper.updateWaitPendingList(bean,roleFlag);
            return true;
        }
        catch (Exception e)
        {
            log.error("发送通知页面--更新待发送列表:刷新数据失败出现异常:{}", e.getMessage(),e);
            throw new RuntimeException("发送通知页面--更新待发送列表:刷新数据异常: " + e.getMessage());
        }
    }

    /**
     * @Description TODO 校验邮件是否可以发送和发送邮件的数量
     * @author wangfenglong
     * @date 2026/4/24 16:08
    **/
    @Override
    public SendCheckResponse checkEmailSend(Integer round, Boolean isALL, List<String> sequenceNumbers, Map<String, Object> filters, String sendFlag)
    {
        try
        {
            List<Integer> countList = new ArrayList<>();
            if (Boolean.TRUE.equals(isALL))
            {
                countList = getByFilters(filters,sendFlag);// 全选模式：使用过滤条件查询记录
                log.info("全选模式查询到条记录，过滤条件: {}", filters);
            }
            else
            {
                if (sequenceNumbers == null || sequenceNumbers.isEmpty()) return new SendCheckResponse(false, 0, 0, 0, 0);
                countList = getBySequenceNumbers(sequenceNumbers,sendFlag);//查询round<6的数据(round表示发送邮件的次数)，一个应用最多发6次邮件 // 非全选模式：使用序列号列表查询记录
            }
            if (CollectionUtil.isEmpty(countList)) return new SendCheckResponse(false, 0, 0, 0, 0);

            int lineManagerEmailCount = countList.get(1);
            int bpoEmailCount = countList.get(0);
            int totalEmailCount = lineManagerEmailCount + bpoEmailCount;
            boolean exceedsLimit = totalEmailCount > 100000;

            log.info("邮件发送检查结果: 轮次={}, 选中记录={}, Line Manager邮件={}, BPO邮件={}, 总邮件={}, 超出限制={}", round, totalEmailCount, lineManagerEmailCount, bpoEmailCount, totalEmailCount, exceedsLimit);
            return new SendCheckResponse(exceedsLimit, lineManagerEmailCount, bpoEmailCount, totalEmailCount, totalEmailCount);
        }
        catch (Exception e)
        {
            log.error("邮件发送检查失败: round={}, isALL={}, sequenceNumbers={}, filters={}", round, isALL, sequenceNumbers, filters, e);
            throw new RuntimeException("邮件发送检查失败: " + e.getMessage());
        }
    }

    /**
     * @Description TODO 发送邮件前的校验--(校验)全选模式：使用过滤条件查询记录
     * @author wangfenglong
     * @date 2026/4/24 16:09
    **/
    public List<Integer> getByFilters(Map<String, Object> filters,String sendFlag)
    {
        try
        {

            Map<String, Object> countMap = new HashMap<>();
            List<Integer> countList = new ArrayList<>();
            UseAccessReviewBean  bean = new UseAccessReviewBean();

            //构建查询条件
            if (filters != null)
            {
                BatchSendRequest request = new BatchSendRequest();
                request.setFilters(filters);
                bean = this.getCurrentRequestFilters(request);
            }

            //获取当前账户的角色
            EmailFailedBean roleBean = this.getCurrentUserRole();
            String roleFlag = roleBean.getRoleFlag();
            if("3".equals(roleFlag))
            {
                bean.setCmdbIdList(roleBean.getCmdbIdList());
            }

            if("SendEmail".equals(sendFlag))
            {
                countMap = tempMapper.getBpoAndLineManagerCountByDistinct(bean, roleFlag);
            }
            else if("failSendEmail".equals(sendFlag))
            {
                countMap = tempMapper.getBpoAndLineManagerCountByDistinctForFailedSend(bean, roleFlag);
            }
            else
            {
                countMap.put("bpocount", 0L);
                countMap.put("linemanagercount", 0L);
            }
            Long bpoCountLong = (Long)countMap.get("bpocount");
            Long lineManagerCountLong = (Long)countMap.get("linemanagercount");
            int bpoCount = bpoCountLong == null ? 0 : bpoCountLong.intValue();
            int lineManagerCount = lineManagerCountLong == null ? 0 : lineManagerCountLong.intValue();
            countList.add(bpoCount);
            countList.add(lineManagerCount);
            return countList;
        }
        catch (Exception e)
        {
            log.error("根据过滤条件查询记录失败: filters={}", filters, e);
            throw new RuntimeException("根据过滤条件查询记录失败: " + e.getMessage());
        }
    }

    /**
     * @Description TODO 邮件发送前的校验--(校验)非全选模式：使用序列号列表查询记录
     * @author wangfenglong
     * @date 2026/4/24 16:09
    **/
    public List<Integer> getBySequenceNumbers(List<String> sequenceNumbers,String sendFlag)
    {
        List<Integer> countList = new ArrayList<>();
        int bpoCount = 0;
        int lineManagerCount = 0;
        if("SendEmail".equals(sendFlag))
        {
            bpoCount = tempMapper.getBpoCountDistinctBySequenceNumbers(sequenceNumbers);
            lineManagerCount = tempMapper.getLineManagerCountDistinctBySequenceNumbers(sequenceNumbers);
        }
        if("failSendEmail".equals(sendFlag))
        {
            bpoCount = tempMapper.getBpoCountDistinctBySequenceNumbersForFailedSendEmail(sequenceNumbers);
            lineManagerCount = tempMapper.getLineManagerCountDistinctBySequenceNumbersForFailedSendEmail(sequenceNumbers);
        }
        countList.add(bpoCount);
        countList.add(lineManagerCount);
        return countList;
    }





    @Override
    public Page<UserAccessReview> queryHistoryRecords(Integer page, Integer size, UseAccessReviewBean useAccessReviewBean)
    {
        try {
            Page<UserAccessReview> p = new Page<>(page, size);
            // 优化查询速率
            Page<UserAccessReview> result = userAccessReviewMapper.selectHistoryRecords(p, useAccessReviewBean);
//            下面的代码是小信封，暂时注释
//            result.getRecords().forEach(review -> {
//                String tempUuid = review.getUuid();
//                review.setLineManagerEmailStatus(
//                        emailSendService.getByReviewUuidAndRecipientType(tempUuid,"LineManager")
//                );
//                review.setBpoEmailStatus(
//                        emailSendService.getByReviewUuidAndRecipientType(tempUuid,"BPO")
//                );
//            });
            return result;
        } catch (Exception e) {
            log.error("查询历史记录失败", e);
            throw new RuntimeException("查询历史记录失败: " + e.getMessage());
        }
    }

    @Override
    public List<UserAccessReview> getAllHistoryRecords(UseAccessReviewBean useAccessReviewBean, String language)
    {
        List<UserAccessReview> reviews = userAccessReviewMapper.selectAllHistoryRecords(useAccessReviewBean, language);
        reviews.forEach(review -> review.setAccessLabel(
                regionalUarPolicyService.formatAccessLabelForExport(review.getAccessLabel(), language)
        ));
        return reviews;
    }

    @Override
    @Transactional
    public boolean localMergeUarDoUpdate(String cmdbId)
    {
        try {
            if (itsApplicationAccessDataServiceMapper.countByCmdbId(cmdbId) <= 0) {
                throw new RuntimeException("数据源未接入ITSI");
            } else{
                // 1.检查当前应用是否已经开启周期
                UarCycleMaintenance cycle = uarCycleMaintenanceMapper.queryLatestByCmdbId(cmdbId);
                if (cycle == null) {
                    throw new RuntimeException("当前应用未开启周期");
                }

                // 2.批量初始化
                userAccessReviewMapper.localMergeUarDoUpdateByCmdbId(
                        cmdbId, String.valueOf(cycle.getId()), cycle.getAccessReviewScope());

                // 3.标记数据来源：同步
                itsApplicationDataMapper.updateDataReadyByCmdbId(cmdbId, "SYNC");
                return true;
            }
        } catch (Exception e) {
            log.error("同步实时数据失败: cmdbId={}", cmdbId, e);
            throw new RuntimeException("同步实时数据失败: " + e.getMessage());
        }
    }

    @Transactional
    @Override
    public ImportResult importUarData(MultipartFile file, String cmdbId) throws IOException
    {
        UarCycleMaintenance cycle = uarCycleMaintenanceMapper.queryLatestByCmdbId(cmdbId);
        if (cycle == null) {
            throw new IllegalStateException("当前应用未开启周期");
        }
        // 清空原有数据
        userAccessReviewMapper.truncateByCmdbId(cmdbId);

        // 创建临时文件
        Path tempFile = Files.createTempFile("excel-import-uar" + System.currentTimeMillis(), ".xlsx");
        try {
            // 将上传文件写入临时文件
            file.transferTo(tempFile.toFile());

            // 使用临时文件路径进行流式解析
            ImportResult importResult = parseExcelStreaming(tempFile, cmdbId, cycle);
            if (importResult.getSuccessCount() > 0) {
                // 调整应用状态为已准备
                itsApplicationDataMapper.updateDataReadyByCmdbId(cmdbId, "IMPORT");
            }
            return importResult;
        } finally {
            // 确保删除临时文件
            Files.deleteIfExists(tempFile);
        }
    }

    private ImportResult parseExcelStreaming(Path filePath, String cmdbId, UarCycleMaintenance cycle) throws IOException
    {
        List<String> errors = new ArrayList<>();
        int errorCount = 0;
        List<UserAccessReview> validRecords = new ArrayList<>();
        // 使用 AtomicInteger 替代基本类型 int
        int successCount = 0;
        AtomicInteger ignoreCount = new AtomicInteger(0);

        // 应用的周期信息
        String uarId = String.valueOf(cycle.getId());
        // 忽略导入的Role
        List<String> ignoreRoles = uarIgnoreRuleMapper.getEntitiesByCmdbId(cmdbId);


        // 内网使用，文件来源可信，关闭 Zip Bomb 检测以避免误报
        ZipSecureFile.setMinInflateRatio(0);

        try (OPCPackage pkg = OPCPackage.open(filePath.toFile())) {
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings strings = new ReadOnlySharedStringsTable(pkg);
            StylesTable styles = reader.getStylesTable();

            XMLReader parser = SAXHelper.newXMLReader();
            String operator = SecurityUtils.getCurrentUserId();

            SheetHandler handler = new SheetHandler(styles, strings, errors, validRecords, ignoreCount, operator,
                    cmdbId, uarId, cycle.getAccessReviewScope(), ignoreRoles);
            parser.setContentHandler(new XSSFSheetXMLHandler(styles, strings, handler, false));

            try (InputStream sheetStream = reader.getSheetsData().next()) {
                parser.parse(new InputSource(sheetStream));
            }

            errorCount = errors.size();

            if (validRecords.isEmpty()) {
                return new ImportResult(0, errorCount, ignoreCount.get(), errors);
            }
            // 去重查询
            List<String> excelItCodes = validRecords.stream().map(UserAccessReview::getItCodeOfUser).distinct().collect(Collectors.toList());
            List<LenovoUser> userAndManagerInfo = lenovoUserMapper.getUserAndManagerInfo(excelItCodes);

            // 1. 查到的用户信息转成 Map，方便 O(1) 查找
            Map<String, LenovoUser> userMap = userAndManagerInfo.stream()
                    .collect(Collectors.toMap(LenovoUser::getUserName, u -> u, (a, b) -> a));

            Set<String> missingUsers = new HashSet<>();
            Set<String> noManagerUsers = new HashSet<>();


            Iterator<UserAccessReview> it = validRecords.iterator();
            while (it.hasNext()) {
                UserAccessReview review = it.next();
                LenovoUser user = userMap.get(review.getItCodeOfUser());

                if (user == null) {
                    errorCount++;
                    errors.add("ITCode："+ review.getItCodeOfUser() + " not found");
                    missingUsers.add(review.getItCodeOfUser());
                    it.remove();
                    continue;
                }
                if (user.getManager1st() == null) {
                    errorCount++;
                    errors.add( "ITCode："+ review.getItCodeOfUser() + " has no manager");
                    noManagerUsers.add(review.getItCodeOfUser());
                    it.remove();
                    continue;
                }

                review.setLineManager(user.getManager1st());
                review.setLineManagerEmail(user.getLineManagerEmail());
                review.setEmail(user.getEmail());
                review.setUserRealName(user.getRealName());
                review.setCountry(user.getCountry());
                review.setDept(user.getLenovoDept());
                review.setLineManagerLevelCode(user.getLineManagerLevelCode());
                review.setLineManagerBandEdFlag(user.getLineManagerBandFlag());
                String userCocType = user.getCocFlag() == null
                        ? null
                        : user.getCocFlag().trim().toUpperCase(Locale.ROOT);
                review.setUserCocType(
                        "Y".equals(userCocType) || "N".equals(userCocType)
                                ? userCocType
                                : null
                );

            }

            // 统一报错
            if (!missingUsers.isEmpty()) {
                errors.add(0, "The following ITCode information cannot be found : " + String.join(", ", missingUsers));
            }
            if (!noManagerUsers.isEmpty()) {
                errors.add(0, "The following users lack Line Manager information: " + String.join(", ", noManagerUsers));
            }

            // 插入
            if (!validRecords.isEmpty()) {
                // 如果总数可能超过安全值，再分 1000 一批
                int batchSize = 1000;
                for (int i = 0; i < validRecords.size(); i += batchSize) {
                    List<UserAccessReview> batch = validRecords.subList(
                            i, Math.min(i + batchSize, validRecords.size())
                    );
                    successCount += getBaseMapper().batchSave(batch, operator);
                }
                validRecords.clear();
            }
        } catch (Exception e) {
            log.error(String.valueOf(e.getMessage()));

            String msg = e.getMessage();
            if (msg == null || !msg.contains("uq_uar_unique")) {
                throw new IOException("Excel parsing failed", e);
            }

            int start = msg.indexOf("=(") + 2;
            int end = msg.indexOf(")", start);

            String[] vals = msg.substring(start, end).split(",\\s*");
            throw new IOException("Data is duplicated, please check：ITCode: " + vals[1] + ", SystemRole：" + vals[4], e);
        }
        return new ImportResult(successCount, errorCount, errors, ignoreCount.get());
    }

    private class SheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler
    {
        private final StylesTable styles;
        private final SharedStrings strings;
        private final List<String> errors;
        private final List<UserAccessReview> validRecords;
        private final AtomicInteger ignoreCount;
        private final String operator;
        private final String cmdbId;
        private final String uarId;
        private final AccessReviewScope accessReviewScope;
        private final List<String> ignoreRoles;

        private List<String> currentRow = new ArrayList<>();
        private int currentRowIndex = -1;

        private final Map<String, Integer> columnMapping = new HashMap<>();

        private final Map<String, List<String>> fieldTitles = Map.of(
                "cmdbId", List.of("CMDB ID", "CmdbID"),
                "itCodeOfUser", List.of("用户 IT Code", "User IT Code"),
                "userId", List.of("用户账号(选填)", "User Account(optional)"),
                "systemRoleId", List.of("系统角色编号", "System Role Id"),
                "systemRole", List.of("系统角色", "System Role"),
                "roleDescription", List.of("系统角色描述", "Role Description"),
                "bpo", List.of("BPO ITCode", "BPO"),
                "bpoEmail", List.of("BPO邮箱", "BPO Email"),
                "accessLabel", List.of("角色分类", "Role Classification")
        );

        public SheetHandler(
                StylesTable styles, SharedStrings strings,
                List<String> errors, List<UserAccessReview> validRecords,
                AtomicInteger ignoreCount, String operator, String cmdbId,
                String uarId, AccessReviewScope accessReviewScope, List<String> ignoreRoles
        ) {
            this.styles = styles;
            this.strings = strings;
            this.errors = errors;
            this.validRecords = validRecords;
            this.ignoreCount = ignoreCount;
            this.operator = operator;
            this.cmdbId = cmdbId;
            this.uarId = uarId;
            this.accessReviewScope = accessReviewScope;
            this.ignoreRoles = ignoreRoles;
        }

        @Override
        public void startRow(int rowIndex) {
            currentRowIndex = rowIndex;
            currentRow.clear();
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int columnIndex = CellReference.convertColStringToIndex(
                    cellReference.replaceAll("\\d", ""));

            // 填充缺失的列
            while (currentRow.size() <= columnIndex) {
                currentRow.add("");
            }
            currentRow.set(columnIndex, formattedValue);
        }

        @Override
        public void endRow(int rowIndex) {
            try {
                if (columnMapping.isEmpty()) {
                    mapHeadersToFields(currentRow);
                    return;
                }

                String cmdbIdValue = getFieldValue("cmdbId");
                String systemRoleValue = getFieldValue("systemRole");
                String itCodeOfUserValue = getFieldValue("itCodeOfUser");
                String accessLabelValue = normalizeAccessLabel(getFieldValue("accessLabel"));

                if (cmdbIdValue.isEmpty()) {
                    return;
                }

                if (!cmdbId.equals(cmdbIdValue)) {
                    errors.add(String.format("%d line: Not data for the current cmdb", rowIndex + 1));
                    return;
                }
                if (systemRoleValue.isEmpty()) {
                    errors.add(String.format("%d line: [SystemRole] cannot be empty", rowIndex + 1));
                    return;
                }
                if (itCodeOfUserValue.isEmpty()) {
                    errors.add(String.format("%d line: [ItCode] cannot be empty", rowIndex + 1));
                    return;
                }
                if (!"standard".equals(accessLabelValue) && !"sensitive".equals(accessLabelValue)) {
                    errors.add(String.format(
                            "%d line: [角色分类/Role Classification] must be 一般权限/敏感权限 or General Access Rights/Sensitive Access Rights",
                            rowIndex + 1));
                    return;
                }
                if (accessReviewScope.isSensitiveOnly() && !"sensitive".equals(accessLabelValue)) {
                    ignoreCount.incrementAndGet();
                    return;
                }
                // 匹配忽略规则
                if (matchIgnore(ignoreRoles, systemRoleValue)) {
                    ignoreCount.incrementAndGet();
                    return;
                }

                UserAccessReview review = new UserAccessReview();
                review.setCmdbId(cmdbIdValue);
                review.setUarId(uarId);
                review.setItCodeOfUser(itCodeOfUserValue);
                review.setUserName(itCodeOfUserValue);
                review.setUserId(getFieldValue("userId"));
                review.setSystemRoleId(getFieldValue("systemRoleId"));
                review.setSystemRole(systemRoleValue);
                review.setRoleDescription(getFieldValue("roleDescription"));
                review.setLeitSystemId(getFieldValue("systemRoleId"));
                review.setBpo(replaceSymbols(getFieldValue("bpo")));
                review.setBpoEmail(replaceSymbols(getFieldValue("bpoEmail")));
                review.setAccessLabel(accessLabelValue);
                review.setUuid(UUID.randomUUID().toString());

                validRecords.add( review );

            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e.getMessage());
            } finally {
                currentRow.clear();
            }
        }

        private String replaceSymbols(String value) {
            return value.replaceAll("[，,；]", ";");
        }

        /**
         * 判断 systemRole 是否应该被忽略
         *
         * @param ignoreRoles  规则列表（从 ignore_rule 表读取的 system_role 集合）
         * @param systemRole   待匹配的角色字符串
         * @return true = 应该忽略；false = 保留
         */

        private boolean matchIgnore(List<String> ignoreRoles, String systemRole) {
            if (ignoreRoles == null || ignoreRoles.isEmpty() || ignoreRoles == null) {
                return false;
            }

            boolean hasSharpMatch = false;   // 是否被 # 规则命中（例外，不忽略）
            boolean hasStarMatch = false;    // 是否被 * 规则命中（忽略）
            boolean hasExactMatch = false;   // 是否被精确规则命中（忽略）

            for (String rule : ignoreRoles) {
                if (rule == null || rule.isEmpty()) {
                    continue;
                }

                boolean hasStar = rule.contains("*");
                boolean hasSharp = rule.contains("#");

                if (!hasStar && !hasSharp) {
                    // 精确匹配规则
                    if (systemRole.equals(rule)) {
                        hasExactMatch = true;
                    }
                } else if (hasSharp) {
                    // # 例外规则：匹配则保留（不忽略）
                    if (matchWithWildcard(systemRole, rule, '#')) {
                        hasSharpMatch = true;
                    }
                } else if (hasStar) {
                    // * 模糊规则：匹配则忽略
                    if (matchWithWildcard(systemRole, rule, '*')) {
                        hasStarMatch = true;
                    }
                }
            }

            // 优先级： 精确匹配 > # 例外 > * 模糊
            if (hasExactMatch) {
                return true;   // 精确匹配，忽略
            }
            if (hasSharpMatch) {
                return false;  // 被 # 规则捞回来，不忽略
            }
            if (hasStarMatch) {
                return true;   // 模糊匹配，忽略
            }
            return false;      // 都不命中，保留

        }


        /**
         * 使用通配符匹配（支持 SQL LIKE 语义）
         *
         * @param text       待匹配的文本
         * @param pattern    通配符模式（含 * 或 #）
         * @param wildcard   通配符字符（* 或 #）
         * @return true = 匹配成功
         */
        private  boolean matchWithWildcard(String text, String pattern, char wildcard) {
            // 将通配符模式转为正则表达式
            // 1. 先转义正则特殊字符
            // 2. 将 wildcard 替换为 .*（任意字符任意次数）
            // 3. 将 _ 替换为 .（单个字符，SQL LIKE 语义）

            StringBuilder regex = new StringBuilder();
            regex.append("^");

            for (char c : pattern.toCharArray()) {
                if (c == wildcard) {
                    regex.append(".*");
                } else if (c == '_') {
                    regex.append(".");  // SQL LIKE 中 _ 匹配单个字符
                } else if ("\\.^$+?{}[]|()".indexOf(c) >= 0) {
                    regex.append("\\").append(c);
                } else {
                    regex.append(c);
                }
            }
            regex.append("$");

            return text.matches(regex.toString());
        }

        private void mapHeadersToFields(List<String> headerRow) {
            for (int i = 0; i < headerRow.size(); i++) {
                String header = headerRow.get(i).trim();
                int columnIndex = i;
                fieldTitles.forEach((field, titles) -> {
                    if (titles.stream().anyMatch(title -> title.equalsIgnoreCase(header))) {
                        columnMapping.put(field, columnIndex);
                    }
                });
            }
            if (!columnMapping.keySet().containsAll(List.of("cmdbId", "itCodeOfUser", "systemRole", "accessLabel"))) {
                columnMapping.clear();
            }
        }

        private String getFieldValue(String field) {
            Integer index = columnMapping.get(field);
            return index != null && index < currentRow.size() ? currentRow.get(index).trim() : "";
        }

        private String normalizeAccessLabel(String value) {
            if ("敏感权限".equalsIgnoreCase(value) || "Sensitive Access Rights".equalsIgnoreCase(value)
                    || "sensitive".equalsIgnoreCase(value)) {
                return "sensitive";
            }
            if ("一般权限".equalsIgnoreCase(value) || "General Access Rights".equalsIgnoreCase(value)
                    || "standard".equalsIgnoreCase(value)) {
                return "standard";
            }
            return value;
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
            // 不需要实现
        }
    }

    @Override
    public List<String> getDistinctSystemRolesByCmdbId(String cmdbId, String bpo) {
        return baseMapper.selectDistinctSystemRolesByCmdbId(cmdbId, bpo);
    }

    @Override
    public Map<String, String> relatedLeaderAndSave(List<String> employees) {
        HashMap<String, String> result = new HashMap<>();
        Integer i = userAccessReviewMapper.relatedLeaderAndSave(employees);
        result.put("success", String.valueOf(i));
        if (employees.size() != i) {
            List<String> finalRemoveUser = userAccessReviewMapper.findFinalRemoveUser();
            employees.removeAll(finalRemoveUser);
            result.put("fail", String.valueOf(employees.size()));
            result.put("failUser", employees.toString());
        }
        return result;
    }

    @Transactional
    @Override
    public ImportResult importUARExceptionProcessData(MultipartFile file, List<String> dataRange) throws IOException
    {
        // 创建临时文件
        Path tempFile = Files.createTempFile("excel-import-uar-exception" + System.currentTimeMillis(), ".xlsx");
        try {
            // 将上传文件写入临时文件
            file.transferTo(tempFile.toFile());

            // 使用临时文件路径进行流式解析
            ImportResult importResult = parseExcelStreaming(tempFile, dataRange);

            return importResult;
        } finally {
            // 确保删除临时文件
            Files.deleteIfExists(tempFile);
        }
    }

    private ImportResult parseExcelStreaming(Path filePath, List<String> dataRange) throws IOException
    {
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        List<UserAccessReview> validRecords = new ArrayList<>();

        // 内网使用，文件来源可信，关闭 Zip Bomb 检测以避免误报
        ZipSecureFile.setMinInflateRatio(0);

        try (OPCPackage pkg = OPCPackage.open(filePath.toFile())) {
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings strings = new ReadOnlySharedStringsTable(pkg);
            StylesTable styles = reader.getStylesTable();

            XMLReader parser = SAXHelper.newXMLReader();
            String operator = SecurityUtils.getCurrentUserId();

            ExceptionSheetHandler handler = new ExceptionSheetHandler(validRecords);
            parser.setContentHandler(new XSSFSheetXMLHandler(styles, strings, handler, false));

            try (InputStream sheetStream = reader.getSheetsData().next()) {
                parser.parse(new InputSource(sheetStream));
            }

            if (validRecords.isEmpty()) {
                errors.add("No valid data available");
                return new ImportResult(0, 0, errors);
            }
            // 运维人是否有这个应用的操作权限
            List<String> excelCmdbIds = validRecords.stream().map(UserAccessReview::getCmdbId).distinct().collect(Collectors.toList());
            List<String> removeCmdbIds = new ArrayList<>();
            if (dataRange.isEmpty() || dataRange.get(0).equals("None")) {
                throw new BadRequestException("The current user does not have permission to import these data");
            } else if (dataRange.get(0).equals("*")) {
                // 走个逻辑, 直接过
            } else {
                // 处理 validRecords 数据不属于 当前用户业务范围 摘除
                validRecords.removeIf(record -> !dataRange.contains(record.getCmdbId()));
                // 得到摘除的cmdbId用来返回展示通知
                removeCmdbIds = excelCmdbIds.stream().filter(cmdbId -> !dataRange.contains(cmdbId)).collect(Collectors.toList());
            }

            // 统一报错
            if (!removeCmdbIds.isEmpty()) {
                errors.add("The following CmdbId are not within your business scope: " + String.join(", ", removeCmdbIds));
            }

            // 插入
            if (!validRecords.isEmpty()) {
                // 如果总数可能超过安全值，再分 1000 一批
                successCount = validRecords.size();
                int batchSize = 1000;
                for (int i = 0; i < validRecords.size(); i += batchSize) {
                    List<UserAccessReview> batch = validRecords.subList(
                            i, Math.min(i + batchSize, validRecords.size())
                    );
                    getBaseMapper().batchUpdateExceptionProcess(batch, operator);
                }
                validRecords.clear();
            }
        } catch (Exception e) {
            log.error(String.valueOf(e.getMessage()));

            String msg = e.getMessage();
            if (msg == null || !msg.contains("uq_uar_unique")) {
                throw new IOException("Excel parsing failed", e);
            }

            int start = msg.indexOf("=(") + 2;
            int end = msg.indexOf(")", start);

            String[] vals = msg.substring(start, end).split(",\\s*");
            throw new IOException("Data is duplicated, please check：ITCode: " + vals[1] + ", SystemRole：" + vals[4], e);
        }
        return new ImportResult(successCount, errors.size(), errors);
    }


    private static class ExceptionSheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler
    {
        private final List<UserAccessReview> validRecords;

        private List<String> currentRow = new ArrayList<>();
        private int currentRowIndex = -1;

        public ExceptionSheetHandler(List<UserAccessReview> validRecords) {
            this.validRecords = validRecords;
        }

        @Override
        public void startRow(int rowIndex) {
            currentRowIndex = rowIndex;
            currentRow.clear();
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int columnIndex = CellReference.convertColStringToIndex(
                    cellReference.replaceAll("\\d", ""));

            // 填充缺失的列
            while (currentRow.size() <= columnIndex) {
                currentRow.add("");
            }
            currentRow.set(columnIndex, formattedValue);
        }

        @Override
        public void endRow(int rowIndex) {
            try {
                // 跳过标题行(第一行)
                if (rowIndex == 0) return;

                String sequenceNumberValue = getValue(0);
                String cmdbIdValue = getValue(1);
                String exceptionTicketNoValue = getValue(25);
                String exceptionResultDecisionValue = getValue(26);
                String exceptionReasonValue = getValue(27);

                if (cmdbIdValue.isEmpty() || sequenceNumberValue.isEmpty()) {
                    return;
                }
                if (exceptionTicketNoValue.isEmpty() || exceptionResultDecisionValue.isEmpty() || exceptionReasonValue.isEmpty()) {
                    return;
                }

                UserAccessReview review = new UserAccessReview();
                review.setCmdbId(cmdbIdValue);
                review.setSequenceNumber(sequenceNumberValue);
                review.setExceptionTicketNo(exceptionTicketNoValue);
                review.setExceptionResultDecisionParam(exceptionResultDecisionValue);
                review.setExceptionReason(exceptionReasonValue);
                validRecords.add( review );

            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e.getMessage());
            } finally {
                currentRow.clear();
            }
        }

        private String getValue(int index) {
            return (index < currentRow.size()) ? currentRow.get(index).trim() : "";
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
            // 不需要实现
        }
    }
}
