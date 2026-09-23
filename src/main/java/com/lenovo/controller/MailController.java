package com.lenovo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lenovo.bean.RiskQueryBean;
import com.lenovo.entity.MailTemplate;

import com.lenovo.service.CaptureSourceFromService;
import com.lenovo.service.MailTemplateService;
import com.lenovo.service.NotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.ResponseEntity.ok;

@RequestMapping("/mail")
@RestController
@RequiredArgsConstructor
public class MailController extends BaseController {

    private final MailTemplateService mailTemplateService;
    private final CaptureSourceFromService captureSourceFromService;
    private final NotifyService notifyService;

    @GetMapping("/banners")
    public ResponseEntity getBanners() {

        return ok(mailTemplateService.getBanners());
    }

    @GetMapping("/sourceFrom")
    public ResponseEntity getSourceFrom() {
        return ok(captureSourceFromService.list());
    }


    @GetMapping("/template")
    public ResponseEntity queryTemplate(Integer id) {
        return ok(mailTemplateService.getById(id));
    }

    @PostMapping("/template")
    public ResponseEntity saveTemplate(@RequestBody MailTemplate mailTemplate) {
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("workspace_id", mailTemplate.getWorkspaceId());

        if (mailTemplateService.getOne(queryWrapper) != null) {
            throw new RuntimeException("Template already exists");
        }

        mailTemplateService.save(mailTemplate);
        mailTemplateService.saveCapture(mailTemplate.getId(), mailTemplate.getSourceFroms());

        return ok(mailTemplate);
    }

    @PutMapping("/template/{id}")
    public ResponseEntity updateTemplate(@PathVariable Integer id, @RequestBody MailTemplate mailTemplate) {
        mailTemplate.setId(id);
        mailTemplateService.saveCapture(mailTemplate.getId(), mailTemplate.getSourceFroms());
        return ok(mailTemplateService.updateById(mailTemplate));
    }


    /**
     * user下拉选
     */
    @GetMapping("/receiversList")
    public ResponseEntity receiversList() {

        return ok(mailTemplateService.receiversList());
    }

    /**
     * 邮件群发删除
     */
    @GetMapping("/deleteById")
    public ResponseEntity deleteById (@RequestParam(value = "id") Integer id) {

        return ok(mailTemplateService.deleteById(id));
    }

    @GetMapping("/getAll")
    public ResponseEntity query(String receivers, Integer activeStatus, @RequestParam(value = "page", defaultValue = "1") Integer page,
                                @RequestParam(value = "size", defaultValue = "10") Integer size) {

        MailTemplate mailTemplate =new MailTemplate();
        mailTemplate.setReceivers(receivers);
        mailTemplate.setActiveStatus(activeStatus);
        return ok(mailTemplateService.query(mailTemplate, page, size));
    }

    @PostMapping("/insertTemplate")
    public ResponseEntity insertTemplate( @RequestBody MailTemplate mailTemplate) {
       Integer id= mailTemplateService.safeGetNextId();
       mailTemplate.setId(id);
       Boolean flag=  mailTemplateService.save(mailTemplate);
        mailTemplateService.saveCapture(mailTemplate.getId(), mailTemplate.getSourceFroms());
        return ok(flag);
    }




}
