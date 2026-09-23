package com.lenovo.controller;

import com.lenovo.config.LogOperation;
import com.lenovo.entity.ApplicationAccessLink;
import com.lenovo.service.ApplicationAccessLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/applicationAccessLink")
public class ApplicationAccessLinkController extends BaseController {

    private final ApplicationAccessLinkService applicationAccessLinkService;

    @LogOperation(value = "保存或更新应用权限申请链接", module = "应用权限申请链接", type = LogOperation.OperationType.UPDATE)
    @PostMapping("/saveOrUpdate")
    public ResponseEntity saveOrUpdate(@RequestBody ApplicationAccessLink link) {
        try {
            applicationAccessLinkService.saveOrUpdateLink(link);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "操作成功"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @LogOperation(value = "查询应用权限申请链接", module = "应用权限申请链接", type = LogOperation.OperationType.QUERY)
    @GetMapping("/openQuery")
    public ResponseEntity query(
            @RequestParam(value = "searchKey", required = false) String searchKey
    ) {
        try {
            List<ApplicationAccessLink> result = applicationAccessLinkService.queryList(searchKey);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
