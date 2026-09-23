package com.lenovo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.lenovo.bean.RiskManagementcomplianceStatusBean;
import com.lenovo.bean.RiskQueryBean;
import com.lenovo.entity.CaptureSourceFrom;
import com.lenovo.entity.MailTemplate;
import com.lenovo.entity.S3File;

import com.lenovo.entity.ThirdToken;
import com.lenovo.mapper.MailTemplateMapper;
import com.lenovo.service.MailTemplateService;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author mercury
 * @description 针对表【mail_template】的数据库操作Service实现
 * @createDate 2024-11-25 15:19:12
 */
@Service
public class MailTemplateServiceImpl extends ServiceImpl<MailTemplateMapper, MailTemplate>
        implements MailTemplateService {

    @Override
    public ThirdToken getThirdToken(String adminToken) {
        return getBaseMapper().getThirdToken(adminToken);
    }

    @Override
    public List<CaptureSourceFrom> getSourceFrom(MailTemplate mailTemplate) {
        List<CaptureSourceFrom> captureSourceFroms = getBaseMapper().getSourceFrom(mailTemplate.getId());
        Map<String, String> params = new HashMap<>();
//        params.put("workspace_id", mailTemplate.getWorkspaceId().toString());
        if (mailTemplate.getRepeatString().contains("daily")) {
            params.put("merge", "day");
            params.put("time", "day");
        } else if (mailTemplate.getRepeatString().contains("weekly")) {
            params.put("merge", "week");
            params.put("time", "week");
        } else {
            params.put("merge", "month");
            params.put("time", "month");
        }

        StringSubstitutor stringSubstitutor = new StringSubstitutor(params);


        captureSourceFroms.forEach(captureSourceFrom -> {
            captureSourceFrom.setUrl(stringSubstitutor.replace(captureSourceFrom.getUrl()));
        });
        return captureSourceFroms;
    }

    @Override
    public List<MailTemplate> querySubscriptionForNotify() {
        return getBaseMapper().querySubscriptionForNotify();
    }

    @Override
    public List<String> getBanners() {
        List<S3File> files = getBaseMapper().getBanners();
        return files.stream().map(S3File::signedUrl).collect(Collectors.toList());
    }

    @Override
    public void saveCapture(Integer id, List<CaptureSourceFrom> sourceFroms) {
        getBaseMapper().saveCapture(id, sourceFroms);
    }

    @Override
    public MailTemplate getByWorkspaceId(Integer workspaceId) {
        MailTemplate mailTemplate = getOne(new QueryWrapper<MailTemplate>().eq("workspace_id", workspaceId));
        if (mailTemplate != null) {
            mailTemplate.setSourceFroms(getSourceFrom(mailTemplate));
        }
        return mailTemplate;
    }

    @Override
    public List<RiskManagementcomplianceStatusBean> receiversList() {

        return getBaseMapper().receiversList();

    }

    @Override
    public Integer deleteById(Integer id) {

        return getBaseMapper().deleteById(id);

    }

    @Override
    public Page<MailTemplate> query(MailTemplate mailTemplate, Integer page, Integer size) {
        Page p = new Page(page, size);
        return getBaseMapper().query(p, mailTemplate);
    }

    @Override

    @Transactional
    public Integer safeGetNextId() {
        QueryWrapper<MailTemplate> wrapper = new QueryWrapper<>();
        wrapper.select("MAX(id)");  // 锁定当前最大值
        Object result = getBaseMapper().selectObjs(wrapper).stream().findFirst().orElse(0);
        Integer maxId = (result != null) ? ((Number) result).intValue() : 0;
        return maxId + 1;



}
}




