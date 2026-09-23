package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.RiskManagementcomplianceStatusBean;
import com.lenovo.bean.RiskQueryBean;
import com.lenovo.entity.*;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author mercury
 * @description 针对表【mail_template】的数据库操作Mapper
 * @createDate 2024-11-25 15:19:12
 * @Entity com.lenovo.entity.MailTemplate
 */
public interface MailTemplateMapper extends BaseMapper<MailTemplate> {

    ThirdToken getThirdToken(String name);

    List<CaptureSourceFrom> getSourceFrom(Integer id);

    List<MailTemplate> querySubscriptionForNotify();

    List<S3File> getBanners();

    void saveCapture(@Param("mailId") Integer mailId, @Param("sourceFroms") List<CaptureSourceFrom> sourceFroms);

    List<RiskManagementcomplianceStatusBean> receiversList();
    Page<MailTemplate> query(Page p, @Param("mailTemplate") MailTemplate mailTemplate);

}




