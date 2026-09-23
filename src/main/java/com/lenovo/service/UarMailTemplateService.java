package com.lenovo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lenovo.bean.UarMailTemplateBean;
import com.lenovo.entity.UarMailTemplate;

import java.util.List;
import java.util.Map;

public interface UarMailTemplateService extends IService<UarMailTemplate> {
    Page<UarMailTemplate> query(UarMailTemplateBean bean, Integer page, Integer size);
    String previewTemplate(Long id, Map<String, String> variables);
    String previewTemplateWithCustomContent(String content, Map<String, String> variables);
    String previewTemplateWithDueDate(Long id, Map<String, String> variables);
    String previewTemplateWithStoredDueDate(Long id, Map<String, String> otherVariables);
    UarMailTemplate createTemplateWithAutoVersion(UarMailTemplate template);
    List<UarMailTemplate> getTemplatesByTag(String tag);
    public UarMailTemplate getTemplateByUniqueKey(String tag, String toSomeone, String version);

    // 新增方法：获取特定tag和toSomeone的所有模板
    public List<UarMailTemplate> getTemplatesByTagAndToSomeone(String tag, String toSomeone);

    String previewTemplateWithBanner(Long id, Map<String, String> variables, String bannerUrl);

    //根据tag和toSomeone获取最新版本的模板
    UarMailTemplate findTemplateWithMaxVersion(String tag, String toSomeone);

}