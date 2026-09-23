package com.lenovo.service;

import com.lenovo.entity.FormTemplate;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * @author mercury
 * @description 针对表【form_template】的数据库操作Service
 * @createDate 2024-10-23 11:51:44
 */
public interface FormTemplateService extends IService<FormTemplate> {
    List<FormTemplate> queryTemplate(Integer workspaceId, String templateName, String creator);
}
