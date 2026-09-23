package com.lenovo.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.bean.UarMailTemplateBean;
import com.lenovo.entity.UarMailTemplate;
import com.lenovo.mapper.UarMailTemplateMapper;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.UarMailTemplateService;
import com.lenovo.util.TemplateVariableFixer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UarMailTemplateServiceImpl extends ServiceImpl<UarMailTemplateMapper, UarMailTemplate> implements UarMailTemplateService
{
    private final UarMailTemplateMapper  uarMailTemplateMapper;

    /**
     * @Description TODO 查询所有的邮件模版
     * @author wangfenglong
     * @date 2026/2/9 16:21
    **/
    @Override
    public Page<UarMailTemplate> query(UarMailTemplateBean bean, Integer page, Integer size)
    {
        Page p = new Page(page, size);
        //return getBaseMapper().query(p, bean);
        return uarMailTemplateMapper.getMaxVersion(p, bean);
    }


    @Override
    public String previewTemplate(Long id, Map<String, String> variables) {
        UarMailTemplate template = getById(id);
        if (template == null) {
            throw new RuntimeException("Template not found with id: " + id);
        }

        // 替换变量
        String content = TemplateVariableFixer.replaceTemplateVariables(template.getContent(), variables);
        String banner = template.getBanner(); // 获取banner URL

        // 构建HTML模板
        String htmlTemplate = "<div style=\"background-color: #f2f2f2\">\n" +
                "      <img\n" +
                "        src=\"{{banner}}\"\n" +
                "        alt=\"\"\n" +
                "        width=\"100%\"\n" +
                "      />\n" +
                "      <div style=\"padding: 10px 20px\">\n" +
                "              {{content}}\n" +
                "      </div> \n" +
                "</div>";

        // 替换模板中的banner和content
        return htmlTemplate.replace("{{banner}}", banner != null ? banner : "")
                .replace("{{content}}", content);
    }


    @Override
    public String previewTemplateWithCustomContent(String content, Map<String, String> variables) {
        // 替换变量
        String processedContent = TemplateVariableFixer.replaceTemplateVariables(content, variables);

        // 构建HTML模板
        String htmlTemplate = "<div style=\"background-color: #f2f2f2\">\n" +
                "      <img\n" +
                "        src=\"{{banner}}\"\n" +
                "        alt=\"\"\n" +
                "        width=\"100%\"\n" +
                "      />\n" +
                "      <div style=\"padding: 10px 20px\">\n" +
                "              {{content}}\n" +
                "      </div> \n" +
                "</div>";

        // 替换模板中的content（banner留空）
        return htmlTemplate.replace("{{banner}}", "")
                .replace("{{content}}", processedContent);
    }
    @Override
    public String previewTemplateWithDueDate(Long id, Map<String, String> variables) {
        UarMailTemplate template = getById(id);
        if (template == null) {
            throw new RuntimeException("Template not found with id: " + id);
        }

        // 从变量中获取 dueDate
        String dueDate = variables.get("dueDate");
        if (dueDate != null) {
            // 更新模板的 dueDate
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                template.setDueDate(sdf.parse(dueDate));
                updateById(template);
            } catch (ParseException e) {
                throw new RuntimeException("Invalid dueDate format: " + dueDate, e);
            }
        }

        // 预览模板
        return previewTemplate(id, variables);
    }

    @Override
    public String previewTemplateWithStoredDueDate(Long id, Map<String, String> otherVariables) {
        UarMailTemplate template = getById(id);
        if (template == null) {
            throw new RuntimeException("Template not found with id: " + id);
        }

        // 获取存储的 dueDate
        if (template.getDueDate() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String dueDateStr = sdf.format(template.getDueDate());

            // 将 dueDate 添加到变量中
            if (otherVariables == null) {
                otherVariables = new HashMap<>();
            }
            otherVariables.put("dueDate", dueDateStr);
        }

        // 预览模板
        return previewTemplate(id, otherVariables);
    }

    /**
     * @Description TODO 邮件模版新增
     * @author wangfenglong
     * @date 2025/12/18 14:03
    **/
    @Override
    public UarMailTemplate createTemplateWithAutoVersion(UarMailTemplate template)
    {
        // 设置操作人和操作时间
        template.setOperator(SecurityUtils.getCurrentUsername());
        template.setOperationDate(new Date());

        // 原有的业务逻辑保持不变
        // 验证 toSomeone 是否为有效值
        if (!isValidToSomeone(template.getToSomeone()))
        {
            throw new RuntimeException("toSomeone must be one of: ToApplicationOwner,ToLineManager,ToBPO,ToUarProcessor,ToDelegatee,ToUser");
        }

        // 修复模板变量 - 确保 UAR_ 变量不被拆分
        if (template.getContent() != null)
        {
            String fixedContent = TemplateVariableFixer.fixTemplateVariables(template.getContent());
            template.setContent(fixedContent);
        }

        // 处理 ToApplicationOwner 情况
        if ("ToApplicationOwner".equals(template.getToSomeone()))
        {
            // 对于 ToApplicationOwner，始终使用全局版本计数，忽略 tag
            Integer maxVersion = getBaseMapper().getMaxGlobalVersion();
            template.setVersion(maxVersion != null ? String.valueOf(maxVersion + 1) : "1");

            // 如果提供了 tag，验证是否为数字
            if (template.getTag() != null && !template.getTag().isEmpty() && !isNumeric(template.getTag()))
            {
                throw new RuntimeException("Tag must be a numeric string when provided");
            }
        }
        else
        {
            // 对于非 ToApplicationOwner 情况，tag 是必需的
            if (template.getTag() == null || template.getTag().isEmpty())
            {
                //throw new RuntimeException("Tag is required for non-ToApplicationOwner templates");
                template.setTag("1");
            }

            // 验证tag是否为数字
            if (!isNumeric(template.getTag()))
            {
                throw new RuntimeException("Tag must be a numeric string");
            }

            // 获取相同tag和toSomeone的最大版本号
            Integer maxVersion = getBaseMapper().getMaxVersionByTagAndToSomeone(template.getTag(), template.getToSomeone());

            if (maxVersion == null)
            {
                template.setVersion("1");
            }
            else
            {
                int newVersion = maxVersion + 1;
                template.setVersion(String.valueOf(newVersion));
            }
        }
        save(template);
        return template;
    }



    // 辅助方法：检查 toSomeone 是否为有效值
    private boolean isValidToSomeone(String toSomeone)
    {
        return "ToApplicationOwner".equals(toSomeone)
                || "ToLineManager".equals(toSomeone) //直属经理
                || "ToBPO".equals(toSomeone) //BPO
                || "ToDelegatee".equals(toSomeone) //被委托人
                || "ToUser".equals(toSomeone) //用户
                || "ToUarProcessor".equals(toSomeone); //应用负责人
    }




    // 辅助方法：检查字符串是否为数字
    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        return str.matches("\\d+");
    }
    @Override
    public List<UarMailTemplate> getTemplatesByTag(String tag) {
        LambdaQueryWrapper<UarMailTemplate> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UarMailTemplate::getTag, tag)
                .orderByDesc(UarMailTemplate::getVersion);
        return list(queryWrapper);
    }
    public UarMailTemplate getTemplateByUniqueKey(String tag, String toSomeone, String version) {
        LambdaQueryWrapper<UarMailTemplate> queryWrapper = new LambdaQueryWrapper<>();

        if ("ToApplicationOwner".equals(toSomeone)) {
            // 对于 ToApplicationOwner，唯一性由 (to_someone, version) 确定
            queryWrapper.eq(UarMailTemplate::getToSomeone, toSomeone)
                    .eq(UarMailTemplate::getVersion, version);
        } else {
            // 对于其他情况，唯一性由 (tag, to_someone, version) 确定
            queryWrapper.eq(UarMailTemplate::getTag, tag)
                    .eq(UarMailTemplate::getToSomeone, toSomeone)
                    .eq(UarMailTemplate::getVersion, version);
        }

        return getOne(queryWrapper);
    }

    /**
     * 获取特定 tag 和 toSomeone 的所有版本
     */

    // UarMailTemplateServiceImpl.java
    @Override
    public String previewTemplateWithBanner(Long id, Map<String, String> variables, String bannerUrl) {
        UarMailTemplate template = getById(id);
        if (template == null) {
            throw new RuntimeException("Template not found with id: " + id);
        }

        // 替换变量
        String content = TemplateVariableFixer.replaceTemplateVariables(template.getContent(), variables);

        // 构建HTML模板
        String htmlTemplate = "<div style=\"background-color: #f2f2f2\">\n" +
                "      <img\n" +
                "        src=\"{{banner}}\"\n" +
                "        alt=\"\"\n" +
                "        width=\"100%\"\n" +
                "      />\n" +
                "      <div style=\"padding: 10px 20px\">\n" +
                "              {{content}}\n" +
                "      </div> \n" +
                "</div>";

        // 替换模板中的banner和content
        return htmlTemplate.replace("{{banner}}", bannerUrl != null ? bannerUrl : "")
                .replace("{{content}}", content);
    }
    @Override
    public boolean updateById(UarMailTemplate entity) {
        // 设置操作人和操作时间
        entity.setOperator(SecurityUtils.getCurrentUsername());
        entity.setOperationDate(new Date());
        return super.updateById(entity);
    }

    /**
     * @Description TODO 根据tag和toSomeone获取最新版本的模板
     * @author wangfenglong
     * @date 2026/5/11 15:28
    **/
    @Override
    public UarMailTemplate findTemplateWithMaxVersion(String tag, String toSomeone)
    {
        try
        {
            // 获取相同tag和toSomeone的所有模板
            List<UarMailTemplate> templates = getTemplatesByTagAndToSomeone(tag, toSomeone);

            if (templates == null || templates.isEmpty())
            {
                return null;
            }

            // 找到版本号最大的模板
            return templates.stream()
            .max(Comparator.comparing(t ->
            {
                try
                {
                    return Integer.parseInt(t.getVersion());
                }
                catch (NumberFormatException e)
                {
                    return 0;
                }
            })).orElse(null);
        }
        catch (Exception e)
        {
            log.error("查找最大版本模板失败: tag={}, toSomeone={}", tag, toSomeone, e);
            throw new RuntimeException("查找模板失败: " + e.getMessage());
        }
    }

    public List<UarMailTemplate> getTemplatesByTagAndToSomeone(String tag, String toSomeone) {
        LambdaQueryWrapper<UarMailTemplate> queryWrapper = new LambdaQueryWrapper<>();

        if (tag != null && !tag.isEmpty()) {
            queryWrapper.eq(UarMailTemplate::getTag, tag);
        }

        if (toSomeone != null && !toSomeone.isEmpty()) {
            queryWrapper.eq(UarMailTemplate::getToSomeone, toSomeone);
        }

        return list(queryWrapper);
    }
}