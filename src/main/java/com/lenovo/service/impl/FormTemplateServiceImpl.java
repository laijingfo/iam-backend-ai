package com.lenovo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.entity.FormTemplate;
import com.lenovo.security.utils.StringUtils;
import com.lenovo.service.FormTemplateService;
import com.lenovo.mapper.FormTemplateMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author mercury
 * @description 针对表【form_template】的数据库操作Service实现
 * @createDate 2024-10-23 11:51:44
 */
@Service
public class FormTemplateServiceImpl extends ServiceImpl<FormTemplateMapper, FormTemplate>
        implements FormTemplateService {

    @Override
    public List<FormTemplate> queryTemplate(Integer workspaceId, String templateName, String creator) {
        QueryWrapper<FormTemplate> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(workspaceId != null, "workspace_id", workspaceId);
        queryWrapper.like(StringUtils.isNotEmpty(templateName), "template_name", templateName);
        queryWrapper.eq(StringUtils.isNotEmpty(creator), "creator", creator);
        return list(queryWrapper);
    }
}




