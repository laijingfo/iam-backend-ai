package com.lenovo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lenovo.bean.RiskManagementcomplianceStatusBean;
import com.lenovo.bean.RiskQueryBean;
import com.lenovo.entity.CaptureSourceFrom;
import com.lenovo.entity.MailTemplate;
import com.lenovo.entity.RiskManagement;
import com.lenovo.entity.ThirdToken;

import java.util.List;

/**
 * @author mercury
 * @description 针对表【mail_template】的数据库操作Service
 * @createDate 2024-11-25 15:19:12
 */
public interface MailTemplateService extends IService<MailTemplate> {

    ThirdToken getThirdToken(String adminToken);

    List<CaptureSourceFrom> getSourceFrom(MailTemplate mailTemplate);

    List<MailTemplate> querySubscriptionForNotify();

    List<String> getBanners();

    void saveCapture(Integer id, List<CaptureSourceFrom> sourceFroms);

    MailTemplate getByWorkspaceId(Integer workspaceId);

    public List<RiskManagementcomplianceStatusBean> receiversList();

     Integer deleteById(Integer id);

    Page<MailTemplate> query(MailTemplate mailTemplate, Integer page, Integer size);

    public Integer safeGetNextId();
}
