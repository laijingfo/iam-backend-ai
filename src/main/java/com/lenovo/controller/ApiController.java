// UarMailSendLogController.java
package com.lenovo.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.UarMailSendLogBean;
import com.lenovo.dto.ApiPortalRequest;
import com.lenovo.entity.SysActionLog;
import com.lenovo.entity.UarMailSendLog;
import com.lenovo.service.SysActionLogService;
import com.lenovo.service.UarMailSendLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {
    private final UarMailSendLogService uarMailSendLogService;
    private final SysActionLogService sysActionLogService;

    /**
     * 开放api入口
     *
     * @param apiPortalRequest 请求参数
     * @return 内容
     */
    @PostMapping("/v1/api-portal")
    public ResponseEntity v1ApiPortal(
            @RequestBody ApiPortalRequest apiPortalRequest
    ) {
        String apiName = apiPortalRequest.getApiName();
        if (apiName == null || apiName.isEmpty())
            return ResponseEntity.ok(
                    Map.of( "success", false, "message", "缺少接口参数：'apiName'" )
            );

        try {
            switch (apiName) {
                case "MailSendLog":
                    return getMailSendLog(apiPortalRequest);
                case "MgrAndBpoReviewLog":
                    return getMgrAndBpoReviewLog(apiPortalRequest);
                case "DashboardActionLog":
                    return getDashboardActionLog(apiPortalRequest);
                default:
                    return ResponseEntity.ok(
                            Map.of("success", false, "message", "接口不存在")
                    );
            }
        } catch (Exception e) {
            log.error("接口内部异常：{}", e.getMessage(), e);
            return ResponseEntity.ok(
                    Map.of( "success", false, "message", "接口内部异常")
            );
        }
    }

    /**
     * 获取邮件发送日志
     *
     * @param apiPortalRequest 请求参数
     * @return 内容
     */
    private ResponseEntity getMailSendLog(ApiPortalRequest apiPortalRequest) {
        Page<UarMailSendLog> pageData = uarMailSendLogService.query(
                new UarMailSendLogBean(),
                apiPortalRequest.getPageIndex(),
                apiPortalRequest.getPageSize()
        );

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "list", pageData.getRecords(),
                        "currPage", pageData.getCurrent(),
                        "pageSize", pageData.getSize(),
                        "totalCount", pageData.getTotal(),
                        "totalPage", pageData.getPages()
                )
        );
    }

    /**
     * 获取经理和BPO审核日志
     *
     * @param apiPortalRequest 请求参数
     * @return 内容
     */
    private ResponseEntity getMgrAndBpoReviewLog(ApiPortalRequest apiPortalRequest) {
        List<String> methods = List.of(
                "com.lenovo.controller.UserAccessReviewController.updateBPOReview",
                "com.lenovo.controller.UserAccessReviewController.updateLmAccessReview"
        );

        Page<SysActionLog> pageData = sysActionLogService.getActionLogByMethod(
                apiPortalRequest.getPageIndex(),
                apiPortalRequest.getPageSize(),
                methods
        );

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "list", pageData.getRecords(),
                        "currPage", pageData.getCurrent(),
                        "pageSize", pageData.getSize(),
                        "totalCount", pageData.getTotal(),
                        "totalPage", pageData.getPages()
                )
        );
    }

    /**
     * 获取仪表盘操作日志
     *
     * @param apiPortalRequest 请求参数
     * @return 内容
     */
    private ResponseEntity getDashboardActionLog(ApiPortalRequest apiPortalRequest) {
        List<String> methods = List.of(
                "com.lenovo.controller.DashboardController.queryOverallPage",
                "com.lenovo.controller.DashboardController.queryOverallStatistics",
                "com.lenovo.controller.DashboardController.queryUarFullData",
                "com.lenovo.controller.DashboardController.queryLmLevelStatistics"
        );

        Page<SysActionLog> pageData = sysActionLogService.getActionLogByMethod(
                apiPortalRequest.getPageIndex(),
                apiPortalRequest.getPageSize(),
                methods
        );

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "list", pageData.getRecords(),
                        "currPage", pageData.getCurrent(),
                        "pageSize", pageData.getSize(),
                        "totalCount", pageData.getTotal(),
                        "totalPage", pageData.getPages()
                )
        );
    }


}