package com.lenovo.controller;

import com.baomidou.mybatisplus.core.conditions.query.Query;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.entity.ComplianceControl;
import com.lenovo.service.ComplianceControlService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/compliance")
public class ComplianceController extends BaseController {

    private final ComplianceControlService complianceControlService;

    @GetMapping("/control/category")
    public ResponseEntity controlCategory(@RequestParam String categoryId) {
        return ok(complianceControlService.query().eq("category_id", categoryId).select("compliance_id").list().stream().map(ComplianceControl::getComplianceId).collect(Collectors.toList()));
    }

    @PostMapping("/control")
    public ResponseEntity createControl(@RequestBody ComplianceControl complianceControl) {
        complianceControlService.save(complianceControl);
        return ok(complianceControl);
    }

    @GetMapping("/control")
    public ResponseEntity pageControl(@RequestParam(required = false) String categoryId, @RequestParam(required = false) String category, @RequestParam(required = false) String complianceId, @RequestParam(required = false) String complianceControl, @RequestParam(required = false) Boolean controlStatus, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        Page page1 = Page.of(page, size);
        QueryChainWrapper<ComplianceControl> queryWrapper = complianceControlService.query();
        if (StringUtils.isNotBlank(categoryId)) {
            queryWrapper.eq("category_id", categoryId);
        }
        if (StringUtils.isNotBlank(category)) {
            queryWrapper.eq("category", category);
        }
        if (StringUtils.isNotBlank(complianceId)) {
            queryWrapper.like("compliance_id", complianceId);
        }
        if (StringUtils.isNotBlank(complianceControl)) {
            queryWrapper.like("compliance_control", complianceControl);
        }
        if (controlStatus != null) {
            queryWrapper.eq("control_status", controlStatus);
        }
        return ok(queryWrapper.orderByAsc("id").page(page1));
    }

    @PutMapping("/control/{id}")
    public ResponseEntity updateControl(@PathVariable Integer id, @RequestBody ComplianceControl complianceControl) {
        complianceControlService.updateById(complianceControl);
        return ok(complianceControl);
    }

    @DeleteMapping("/control/{id}")
    public ResponseEntity deleteControl(@PathVariable Integer id) {
        complianceControlService.removeById(id);
        return ok();
    }
}
