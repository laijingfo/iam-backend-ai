package com.lenovo.controller;

import com.lenovo.config.LogOperation;
import com.lenovo.entity.UarCycleSetting;
import com.lenovo.service.UarCycleSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/uarCycleSetting")
@RequiredArgsConstructor
public class UarCycleSettingController {

    private final UarCycleSettingService uarCycleSettingService;

    @PostMapping("/add")
    @LogOperation(module = "UAR周期配置", type = LogOperation.OperationType.ADD, value = "新增周期")
    public ResponseEntity<?> add(@RequestBody UarCycleSetting uarCycleSetting) {
        try {
            return ResponseEntity.ok(Map.of("success",
                    uarCycleSettingService.createCycleSetting(uarCycleSetting)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/query")
    @LogOperation(module = "UAR周期配置", type = LogOperation.OperationType.QUERY, value = "分页查询周期")
    public ResponseEntity<?> query(
            @RequestParam(required = false) String uarName
    ) {
        return ResponseEntity.ok(uarCycleSettingService.queryList(uarName));
    }

    @GetMapping("/getDistinct")
    @LogOperation(module = "UAR周期配置", type = LogOperation.OperationType.QUERY, value = "获取所有周期名称")
    public ResponseEntity<?> getDistinct() {
        return ResponseEntity.ok(uarCycleSettingService.getDistinctList());
    }

    @PutMapping("/update")
    @LogOperation(module = "UAR周期配置", type = LogOperation.OperationType.UPDATE, value = "修改周期配置")
    public ResponseEntity<?> update(@RequestBody UarCycleSetting uarCycleSetting) {
        try {
            return ResponseEntity.ok(Map.of("success",
                    uarCycleSettingService.updateCycleSetting(uarCycleSetting)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/setCurrent/{uarId}")
    @LogOperation(module = "UAR周期配置", type = LogOperation.OperationType.UPDATE, value = "设置为当前周期")
    public ResponseEntity<?> setCurrent(@PathVariable String uarId) {
        if (uarId == null || uarId.isEmpty()) {
            return ResponseEntity.ok(Map.of("success", false, "message", "周期ID不能为空"));
        }
        try {
            return ResponseEntity.ok(Map.of("success", uarCycleSettingService.setCurrent(uarId)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/getCurrentUarId")
    @LogOperation(module = "UAR周期配置", type = LogOperation.OperationType.UPDATE, value = "获取当前周期UarID")
    public ResponseEntity<?> getCurrentUarId() {
        return ResponseEntity.ok(
                Map.of( "success", true, "data", uarCycleSettingService.getCurrentUarId())
        );
    }


}
