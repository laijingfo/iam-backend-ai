package com.lenovo.strategy.impl;

import com.lenovo.bean.BpoItCodeExpireEmailData;
import com.lenovo.bean.ExpiredBpoBean;
import com.lenovo.bean.UarAlertBean;
import com.lenovo.dto.UarAlertRequest;
import com.lenovo.entity.UarMailTemplate;
import com.lenovo.mapper.AlertDashboardMapper;
import com.lenovo.service.UarMailTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Description TODO 处理实效的bpo数据
 * @ClassName ExpiredBpoDataHandler
 * @Author wangfenglong
 * @Date 2026/4/23 09:58
 **/
@Slf4j
public class ExpiredBpoDataHandler
{
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    public static ExpiredBpoBean getExpiredBpoData(UarAlertRequest uarAlertRequest, UarMailTemplateService uarMailTemplateService, AlertDashboardMapper alertDashboardMapper)
    {
        try
        {
            String tag = "5";
            String toSomeone = "ToUarProcessor";
            String sendFlag = "ExpiredBpoItCode";
            String batchNo = String.format("%s_%s_%s_%s_%s", toSomeone,"ExpiredBpoItCode", uarAlertRequest.getItCodeOfUser(), LocalDateTime.now().format(formatter), UUID.randomUUID().toString().replace("-", ""));
            UarMailTemplate bpoTemplate = uarMailTemplateService.findTemplateWithMaxVersion(tag, toSomeone);
            if (Objects.isNull(bpoTemplate))
            {
                //return ResponseEntity.ok(Map.of( "success", false, "message", "处理BPO失效的itCode:失败: 未找到对应邮件模板" ));
                throw new RuntimeException("处理BPO失效的itCode:失败: 未找到对应邮件模板");
            }

            //获取需要发送邮件的数据
            List<UarAlertBean> recipientEmailList = alertDashboardMapper.getNeedDataByAlert(uarAlertRequest);
            if(CollectionUtils.isEmpty(recipientEmailList))
            {
                //return ResponseEntity.ok(Map.of( "success", false, "message", "处理BPO失效的itCode:失败: 获取原始数据recipientEmailList is empty" ));
                throw new RuntimeException("处理BPO失效的itCode:失败: 获取原始数据recipientEmailList is empty");
            }

            //存储的是cmdb_id和total_alert_cmdb对应关系
            Map<String, Integer> cmdbTotalAlertMap = new HashMap<>();

            //存储的key是operation_focal和operation_owner, value是cmdb_id
            //wangfl16 → [A000091, A002873]
            //darrenchen → [A000091]
            //denghuan1 → [A002873]
            //wangli60 → [A004215]
            Map<String,List<String>> itcodeCmdbIdMap = new HashMap<>();

            // 临时用 Set 存储 cmdb_id，自动去重
            Map<String, Set<String>> tempMap = new HashMap<>();

            //最终需要发送的数据
            //EmailName: wangfl16, AppCount: 2, AccessCount: 63
            //EmailName: darrenchen, AppCount: 1, AccessCount: 1
            //EmailName: denghuan1, AppCount: 1, AccessCount: 62
            //EmailName: wangli60, AppCount: 1, AccessCount: 2
            List<BpoItCodeExpireEmailData> dataList = new ArrayList<>();

            for(UarAlertBean uarAlertBean : recipientEmailList)
            {
                String focal = uarAlertBean.getOperationFocal();
                String owner = uarAlertBean.getOperationOwner();
                String cmdbId = uarAlertBean.getCmdbId();
                String totalAlert = uarAlertBean.getTotalAlertCmdb();

                // 这里的取值优先级：有 S&A 就发给 S&A ，没有S&A 就发给运维Owner
                if (cmdbId != null && !cmdbId.trim().isEmpty())
                {
                    if (focal != null && !focal.trim().isEmpty())
                    {
                        tempMap.computeIfAbsent(focal.trim(), k -> new HashSet<>()).add(cmdbId);
                    }
                    else if (owner != null && !owner.trim().isEmpty())
                    {
                        tempMap.computeIfAbsent(owner.trim(), k -> new HashSet<>()).add(cmdbId);
                    }
                }

                // 处理cmdb_id和total_alert_cmdb对应
                if (cmdbId != null && !cmdbId.trim().isEmpty() && totalAlert != null && !totalAlert.trim().isEmpty())
                {
                    cmdbTotalAlertMap.put(cmdbId, Integer.valueOf(totalAlert));
                }
            }

            if(CollectionUtils.isEmpty(tempMap))
            {
                //return ResponseEntity.ok(Map.of( "success", false, "message", "处理BPO失效的itCode:失败: focal和owner is empty" ));
                throw new RuntimeException("处理BPO失效的itCode:失败: focal和owner is empty");
            }

            if(CollectionUtils.isEmpty(cmdbTotalAlertMap))
            {
                //return ResponseEntity.ok(Map.of( "success", false, "message", "处理BPO失效的itCode:失败: cmdbId is empty" ));
                throw new RuntimeException("处理BPO失效的itCode:失败: cmdbId is empty");
            }

            // 将 Set 转换为 List，生成最终 Map
            itcodeCmdbIdMap = tempMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> new ArrayList<>(e.getValue())));

            //处理最终需要发送邮件需要的数据emailName，appCount，accessCount
            for (Map.Entry<String, List<String>> entry : itcodeCmdbIdMap.entrySet())
            {
                String emailName = entry.getKey();
                List<String> cmdbIdList1 = entry.getValue();

                //计算appCount：List的大小
                int appCount = cmdbIdList1.size();

                //计算accessCount：遍历List，查询每个cmdb_id的total_alert_cmdb并求和
                int accessCount = 0;
                for (String cmdbId : cmdbIdList1)
                {
                    if(!CollectionUtils.isEmpty(cmdbTotalAlertMap))
                    {
                        Integer totalAlert = cmdbTotalAlertMap.get(cmdbId);
                        if (totalAlert != null)
                        {
                            accessCount += totalAlert;
                        }
                    }
                }

                // 构建EmailData对象并加入集合
                dataList.add(new BpoItCodeExpireEmailData(emailName, appCount, accessCount));
            }
            if(CollectionUtils.isEmpty(dataList))
            {
                //return ResponseEntity.ok(Map.of( "success", false, "message", "处理BPO失效的itCode:失败: 获取数据dataList is empty" ));
                throw new RuntimeException("处理BPO失效的itCode:失败: 获取数据dataList is empty");
            }
            dataList.forEach(data -> data.setVariables(getVariables2(data)));

            for(BpoItCodeExpireEmailData data : dataList)
            {
                String itCode = data.getEmailName();
                // 空itcode直接跳过，避免报错
                if (itCode == null || itCode.isBlank())
                {
                    continue;
                }
                List<String> emailList = alertDashboardMapper.getEmailByItCode(data.getEmailName());
                // 空 → 拼接
                if(CollectionUtils.isEmpty(emailList) || emailList.get(0) == null || emailList.get(0).isBlank())
                {
                    log.error("给失效的BPO发邮件：处理focal或者owner邮箱：itCode: {} 的邮箱为空，使用默认邮箱: {}", itCode, itCode + "@lenovo.com");
                    data.setEmail(itCode + "@lenovo.com");
                }
                else
                {
                    data.setEmail(emailList.get(0));
                }
            }

            ExpiredBpoBean expiredBpoBean = new ExpiredBpoBean();
            expiredBpoBean.setItCode(uarAlertRequest.getItCodeOfUser());
            expiredBpoBean.setBpoTemplate(bpoTemplate);
            expiredBpoBean.setBatchNo(batchNo);
            expiredBpoBean.setSendFlag(sendFlag);
            expiredBpoBean.setDataList(dataList);
            return expiredBpoBean;
        }
        catch (Exception e)
        {
            log.error("待发送页面-数据处理异常:{}", e.getMessage(), e);
            throw new RuntimeException("处理BPO失效的itCode异常:", e);
        }
    }

    private static Map<String, String> getVariables2(BpoItCodeExpireEmailData data)
    {
        Map<String, String> variables = new HashMap<>();
        //variables.put("{bpoItCode}", data.getEmailName());
        variables.put("{appCount}", ObjectUtils.isNotEmpty(data.getAppCount()) ? String.valueOf(data.getAppCount()) :"0");
        variables.put("{accessCount}", ObjectUtils.isNotEmpty(data.getAccessCount()) ? String.valueOf(data.getAccessCount()) :"0");
        return variables;
    }
}
