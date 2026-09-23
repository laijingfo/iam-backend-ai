package com.lenovo.controller;

import com.lenovo.config.LogOperation;
import com.lenovo.entity.FormFile;
import com.lenovo.entity.FormTemplate;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.FormFileService;
import com.lenovo.service.FormTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RequestMapping("/form")
@RestController
@RequiredArgsConstructor
public class FormController extends BaseController {


    private final FormTemplateService formTemplateService;

    private final FormFileService formFileService;

    @GetMapping("/template")
    public ResponseEntity queryTemplate(@RequestParam(required = false) Integer workspaceId, @RequestParam(required = false) String templateName, @RequestParam(required = false) String creator) {
        return ok(formTemplateService.queryTemplate(workspaceId, templateName, creator));
    }

    @PostMapping("/template")
    public ResponseEntity createTemplate(@RequestBody FormTemplate template) {
        template.setCreator(SecurityUtils.getCurrentUsername());
        formTemplateService.save(template);
        return ok(template);
    }

    @PutMapping("/template/{id}")
    public ResponseEntity updateTemplate(@PathVariable Integer id, @RequestBody FormTemplate template) {
        FormTemplate old = formTemplateService.getById(id);
        if (old == null) {
            throw new RuntimeException("模板不存在");
        }
        old.setTemplateName(template.getTemplateName());
        old.setFormTemplate(template.getFormTemplate());
        formTemplateService.updateById(old);
        return ok(old);
    }

    @DeleteMapping("/template/{id}")
    public ResponseEntity deleteTemplate(@PathVariable Long id) {
        formTemplateService.removeById(id);
        return ok();
    }

    @GetMapping("/file/{id}")
    public ResponseEntity getFile(@PathVariable Long id) {
        FormFile file = formFileService.getAndRefresh(id);
        return ok(file);
    }

    /**
     * @Description TODO 模板管理--图片上传
     * @author wangfenglong
     * @date 2026/1/17 03:16
    **/
    @LogOperation(value = "模板管理-图片上传", module = "表单管理")
    @PostMapping("/file")
    public ResponseEntity uploadFile(MultipartFile file, String fileType, Integer workspaceId)
    {
        FormFile ff = formFileService.uploadFile(file, fileType, workspaceId);
        return ok(ff);
    }
}
