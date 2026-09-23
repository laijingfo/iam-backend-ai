package com.lenovo.controller;

import com.alibaba.fastjson.JSONObject;
import com.lenovo.config.LogOperation;
import com.lenovo.service.UarIgnoreRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * UAR系统同步数据忽略规则
 */
@RestController
@RequestMapping("/useAccessReview/uarIgnoreRule")
@RequiredArgsConstructor
public class UarIgnoreRuleController {

    private final UarIgnoreRuleService uarIgnoreRuleService;

    /**
     *  获取不需要审核的角色
     * @param page
     * @param size
     * @param cmdbId
     * @return
     */
    @LogOperation(value = "UAR系统同步数据忽略规则-获取不需要审核的角色", module = "UAR系统同步数据忽略规则")
    @GetMapping("/{cmdbId}")
    public ResponseEntity getById(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @PathVariable("cmdbId") String cmdbId
    ){
        try {
            return ResponseEntity.ok(uarIgnoreRuleService.getById(page, size, cmdbId));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok().body(
                    Map.of( "message", e.getMessage(), "success", false)
            );
        }
    }

    /**
     *  删除规则
     * @param ruleId
     * @return
     */

    @LogOperation(value = "UAR系统同步数据忽略规则-删除规则", module = "UAR系统同步数据忽略规则")
    @DeleteMapping("/{ruleId}")
    public ResponseEntity deleteRule(@PathVariable Long ruleId){
        try {
            uarIgnoreRuleService.deleteRule(ruleId);
            return ResponseEntity.ok(
                    Map.of( "message", "删除成功", "success", true)
            );
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok().body(
                    Map.of( "message", e.getMessage(), "success", false)
            );
        }
    }


    /**
     *  添加规则
     * @param cmdbId
     * @param jsonObject
     * @return
     */
    @LogOperation(value = "UAR系统同步数据忽略规则-添加规则", module = "UAR系统同步数据忽略规则")
    @PostMapping("/{cmdbId}")
    public ResponseEntity addRule(@PathVariable String cmdbId, @RequestBody JSONObject jsonObject){
        try {
            String systemRole = jsonObject.getString("systemRole").trim();
            if (systemRole == null || systemRole.isEmpty()) {
                return ResponseEntity.ok().body(
                        Map.of( "message", "请填写角色", "success", false)
                );
            }
            if (uarIgnoreRuleService.isExistsRule(cmdbId, systemRole)) {
                return ResponseEntity.ok().body(
                        Map.of( "message", "["+systemRole+"]已存在", "success", false)
                );
            }
            uarIgnoreRuleService.addRule(cmdbId, systemRole);
            return ResponseEntity.ok(
                    Map.of( "message", "添加成功", "success", true)
            );
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.ok().body(
                    Map.of( "message", e.getMessage(), "success", false)
            );
        }
    }
}
