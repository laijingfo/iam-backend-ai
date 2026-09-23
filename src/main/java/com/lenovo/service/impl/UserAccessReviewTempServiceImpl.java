package com.lenovo.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.config.GlobalBusinessStatusEnum;
import com.lenovo.dto.BatchSendRequest;
import com.lenovo.dto.SendCheckResponse;
import com.lenovo.entity.*;
import com.lenovo.mapper.UserAccessReviewMapper;
import com.lenovo.mapper.UserAccessReviewTempMapper;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.security.utils.StringUtils;
import com.lenovo.service.UserAccessReviewService;
import com.lenovo.service.UserAccessReviewTempService;
import com.lenovo.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccessReviewTempServiceImpl extends ServiceImpl<UserAccessReviewTempMapper, UserAccessReviewTemp> implements UserAccessReviewTempService
{
    private final UserAccessReviewTempMapper tempMapper;
    private final RedisUtils redisUtils;
    private final UserAccessReviewMapper userAccessReviewMapper;
    private final UserAccessReviewService userAccessReviewService;

    @Override
    public int getSendEmailTotalCount(String roleFlag, String overallSendStatus,UseAccessReviewBean bean)
    {
        return tempMapper.getSendEmailTotalCount(roleFlag,overallSendStatus,bean);
    }

    /**
     * @Description TODO 校验邮件是否可以发送和发送邮件的数量
     * @author wangfenglong
     * @date 2025/11/25 18:56
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
     * @Description TODO 邮件发送前的校验--(校验)非全选模式：使用序列号列表查询记录
     * @author wangfenglong
     * @date 2025/11/27 09:38
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

    /**
     * @Description TODO 发送邮件前的校验--(校验)全选模式：使用过滤条件查询记录
     * @author wangfenglong
     * @date 2025/11/26 19:15
    **/
    public List<Integer> getByFilters(Map<String, Object> filters,String sendFlag)
    {
        try
        {
            UseAccessReviewBean  bean = new UseAccessReviewBean();
            // 构建查询条件
            if (filters != null)
            {
                BatchSendRequest request = new BatchSendRequest();
                request.setFilters(filters);
                bean = userAccessReviewService.getCurrentRequestFilters(request);
            }

            List<Integer> countList = new ArrayList<>();
            String itCode = SecurityUtils.getCurrentUserId();
            Role roleContainList = (Role)redisUtils.hget(itCode+ GlobalBusinessStatusEnum.REDIS_KEY_USER_ROLE.desc,itCode);
            if(Objects.isNull(roleContainList) || CollectionUtil.isEmpty(roleContainList.getRoleList()))
            {
                return countList;
            }

            List<String> SysITRoleList = List.of("View_Only_IT","UAR_Admin_IT","UAR_System_Admin_IT");//IT权限
            List<String> SysUarRoleList = List.of("UAR_Processer");//UAR权限
            List<String> uniqueRoleList = roleContainList.getRoleList().stream().map(Role::getName).distinct().collect(Collectors.toList());//提取name → 去重 → 收集到List
            Map<String, Object> countMap = new HashMap<>();

            //包含IT角色
            if(uniqueRoleList.stream().anyMatch(SysITRoleList::contains))
            {
                String roleFlag = "1";

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

            //包含UAR角色
            if(uniqueRoleList.stream().anyMatch(SysUarRoleList::contains))
            {
                String roleFlag = "2";
                Optional<Role> uarRoleOpt = roleContainList.getRoleList().stream().filter(role -> GlobalBusinessStatusEnum.UAR_PROCESSER.desc.equals(role.getName())).findFirst();//获取name=UAR_Processer的对象
                Role uarRole = uarRoleOpt.orElse(null); // 无匹配时返回null
                if(Objects.nonNull(uarRole))
                {
                    if(null == uarRole.getCmdbIdOfUarProcesser() || StringUtils.isEmpty(uarRole.getCmdbIdOfUarProcesser()))
                    {
                        Page<UserAccessReviewTemp> result =new Page<>();
                        return countList;
                    }
                    List<String> cmdbIdList = Stream.of(uarRole.getCmdbIdOfUarProcesser().split(",")).collect(Collectors.toList());
                    bean.setCmdbIdList(cmdbIdList);
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
                log.error("查询临时表数据时发生错误:uarRoleOpt是null");
                return countList;
            }
            return countList;
        }
        catch (Exception e)
        {
            log.error("根据过滤条件查询记录失败: filters={}", filters, e);
            throw new RuntimeException("根据过滤条件查询记录失败: " + e.getMessage());
        }
    }

    /**
     * @Description TODO 发送通知页面--更新待发送列表(刷新数据接口)
     * @author wangfenglong
     * @date 2025/11/27 12:48
     **/
    @Override
    @Transactional
    public boolean refreshData()
    {
        try
        {
            //log.info("开始刷新数据...");
            //int updatedCount = resetOverallSendStatus(itCode);
            //log.info("成功将 {} 条记录的发送状态重置为 Pending", updatedCount);
            //重新执行临时表数据填充
            String itCode = SecurityUtils.getCurrentUserId();
            Role roleContainList = (Role)redisUtils.hget(itCode+ GlobalBusinessStatusEnum.REDIS_KEY_USER_ROLE.desc,itCode);
            if(Objects.isNull(roleContainList) || CollectionUtil.isEmpty(roleContainList.getRoleList()))
            {
                log.info("发送通知页面--更新待发送列表:数据刷新失败:roleContainList是null");
                throw new RuntimeException("刷新数据失败: roleContainList是null" );
            }

            List<String> SysITRoleList = List.of("View_Only_IT","UAR_Admin_IT","UAR_System_Admin_IT");//IT权限
            List<String> SysUarRoleList = List.of("UAR_Processer");//UAR权限
            List<String> uniqueRoleList = roleContainList.getRoleList().stream().map(Role::getName).distinct().collect(Collectors.toList());//提取name → 去重 → 收集到List

            //包含IT角色
            if(uniqueRoleList.stream().anyMatch(SysITRoleList::contains))
            {
                String roleFlag = "1";
                userAccessReviewMapper.updateWaitPendingList(new UseAccessReviewBean(),roleFlag);
                return true;
            }

            //包含UAR角色
            if(uniqueRoleList.stream().anyMatch(SysUarRoleList::contains))
            {
                String roleFlag = "2";
                UseAccessReviewBean bean = new UseAccessReviewBean();
                Optional<Role> uarRoleOpt = roleContainList.getRoleList().stream().filter(role -> GlobalBusinessStatusEnum.UAR_PROCESSER.desc.equals(role.getName())).findFirst();//获取name=UAR_Processer的对象
                Role uarRole = uarRoleOpt.orElse(null); // 无匹配时返回null
                if(Objects.nonNull(uarRole))
                {
                    if(null == uarRole.getCmdbIdOfUarProcesser() || com.lenovo.security.utils.StringUtils.isEmpty(uarRole.getCmdbIdOfUarProcesser()))
                    {
                        //Page<UserAccessReviewTemp> result =new Page<>();
                        //return false;
                        //这种情况是允许的
                        log.info("发送通知页面--更新待发送列表:数据刷新失败(当前账户有UARPROCESSER权限但是CmdbIdOfUarProcesser是空):uarRole.getCmdbIdOfUarProcesser()是null");
                        return true;
                    }

                    List<String> cmdbIdList = Stream.of(uarRole.getCmdbIdOfUarProcesser().split(",")).collect(Collectors.toList());
                    bean.setCmdbIdList(cmdbIdList);
                    userAccessReviewMapper.updateWaitPendingList(bean,roleFlag);
                    log.info("发送通知页面--更新待发送列表:更新Sent状态记录成功");
                    return true;
                }
                log.error("发送通知页面--更新待发送列表:发生异常:uarRoleOpt是null");
                throw new RuntimeException("发送通知页面--更新待发送列表:数据异常: uarRoleOpt是null" );
            }

            //包含其他角色则不允许查询更新数据
            log.info("发送通知页面--更新待发送列表:失败: 不具备IT和UarProcesser权限");
            throw new RuntimeException("发送通知页面--更新待发送列表: 不具备IT和UarProcesser权限");
        }
        catch (Exception e)
        {
            log.error("发送通知页面--更新待发送列表:刷新数据失败出现异常:{}", e.getMessage(),e);
            throw new RuntimeException("发送通知页面--更新待发送列表:刷新数据异常: " + e.getMessage());
        }
    }


}