package com.lenovo.controller;

import com.lenovo.config.LogOperation;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.security.utils.StringUtils;
import com.lenovo.service.ArchiveService;
import com.lenovo.service.UarCycleSettingService;
import com.lenovo.util.I18nUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.http.ResponseEntity.ok;

/**
 * @Description TODO 归档数据
 * @ClassName ArchiveController
 * @Author wangfenglong
 * @Date 2026/3/30 17:14
 **/
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/archive")
public class ArchiveController
{
    private final ArchiveService archiveService;
    private final UarCycleSettingService uarCycleSettingService;

    @PostMapping("/archiveData")
    @LogOperation(module = "UAR周期设置", type = LogOperation.OperationType.ADD, value = "归档数据")
    public ResponseEntity<?> archiveData()
    {
        String itCode = SecurityUtils.getCurrentUserId();
        String MAINTENANCE_ID = uarCycleSettingService.getCurrentUarId();
        if(StringUtils.isEmpty(MAINTENANCE_ID))
        {
            return ResponseEntity.ok(Map.of("success", false, "message", I18nUtil.get("custom.archive.error1")));
        }
        archiveService.archiveData(itCode, MAINTENANCE_ID);
        return ok(I18nUtil.get("custom.archive.error2"));
    }
}
