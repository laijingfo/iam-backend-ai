package com.lenovo.controller;

import com.lenovo.bean.AdvancedSearchBean;
import com.lenovo.dto.ApplicationOperationFocalRequest;
import com.lenovo.dto.ApplicationUarProcessorRequest;
import com.lenovo.config.LogOperation;
import com.lenovo.security.utils.RoleUtils;
import com.lenovo.service.ItsApplicationService;
import com.lenovo.service.SyncItsApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/itsApplication")
@RequiredArgsConstructor
public class ApplicationDataController extends BaseController{

    private final ItsApplicationService itsApplicationService;
    private final RoleUtils roleUtils;
    private final SyncItsApplicationService syncItsApplicationService;
    /**
     * 获取业务场景下的下拉选项。
     * scene: alert、alertBpo、dashboard...
     * field: cmdbId、appName、operationDomain、operationTower、operationOwner
     * dashboard 场景必须传 currentDataCycle，是否当前周期由后端判断。
     */
    @LogOperation(value = "获取业务下拉选项", module = "API", type = LogOperation.OperationType.QUERY)
    @GetMapping("/selectorOptions")
    public ResponseEntity getSelectorOptions(
            @RequestParam String scene,
            @RequestParam String field,
            @RequestParam(required = false) String currentDataCycle) {
        return ResponseEntity.ok(
                Map.of(
                        "success", true, "data", itsApplicationService.getSelectorOptions(scene, field, currentDataCycle)
                )
        );
    }

    /**
     * 同步上游应用数据到本系统
     */
    @LogOperation(value = "同步上游应用数据到本系统", module = "API", type = LogOperation.OperationType.SYNC)
    @GetMapping("/syncData")
    public void syncData(){

        syncItsApplicationService.syncSplunkApplicationData();
    }

    /**
     * 同步更新应用数据准备状态
     */
    @LogOperation(value = "同步更新应用的数据准备状态", module = "API", type = LogOperation.OperationType.SYNC)
    @GetMapping("/syncDataReadyStatus")
    public void syncDataReadyStatus(){

        itsApplicationService.syncLocalApplicationReadyData();
    }

    @LogOperation(value = "获取下拉列表应用ID", module = "应用系统UAR周期维护", type = LogOperation.OperationType.QUERY)
    @GetMapping("/getApplicationIds")
    public ResponseEntity getApplicationIds(){
        try {
            List<String> dataRange = roleUtils.getCurrentUserUarBusinessDataRange();
            List<AdvancedSearchBean> data = itsApplicationService.getApplicationMenu("id", dataRange);
            return ResponseEntity.ok(
                    Map.of(
                            "success", true, "data", data
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.ok(
                    Map.of(
                            "success", false,
                            "message", e.getMessage()
                    )
            );
        }
    }

    @LogOperation(value = "获取下拉列表应用名称", module = "应用系统UAR周期维护", type = LogOperation.OperationType.QUERY)
    @GetMapping("/getApplicationNames")
    public ResponseEntity getApplicationNames(){
        try {
            List<String> dataRange = roleUtils.getCurrentUserUarBusinessDataRange();
            List<AdvancedSearchBean> data = itsApplicationService.getApplicationMenu("name", dataRange);
            return ResponseEntity.ok(
                    Map.of(
                            "success", true, "data", data
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.ok(
                    Map.of(
                            "success", false,
                            "message", e.getMessage()
                    )
            );
        }
    }


    /**
     * 更新UAR处理人信息
     * @param bean
     * @return
     */
    @LogOperation(value = "更新UAR处理人信息", module = "应用系统UAR周期维护", type = LogOperation.OperationType.UPDATE)
    @PostMapping("/updateUarProcessor")
    public ResponseEntity updateUarProcessor(@RequestBody ApplicationUarProcessorRequest bean){
        try {
            itsApplicationService.updateUarProcessor(bean);
            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "message", "更新成功"
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.ok(
                    Map.of(
                            "success", false,
                            "message", e.getMessage()
                    )
            );
        }
    }


    /**
     * 更新运维Focal
     * @param bean
     * @return
     */
    @LogOperation(value = "更新运维Focal", module = "更新运维Focal", type = LogOperation.OperationType.UPDATE)
    @PostMapping("/updateOperationFocal")
    public ResponseEntity updateOperationFocal(@RequestBody ApplicationOperationFocalRequest bean){
        try {
            itsApplicationService.updateOperationFocal(bean);
            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "message", "更新成功"
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.ok(
                    Map.of(
                            "success", false,
                            "message", e.getMessage()
                    )
            );
        }
    }

    /**
     * 根据OpOwner获取AppFocal
     * @return
     */
    @LogOperation(value = "根据OpOwner获取Focal", module = "应用系统UAR周期维护", type = LogOperation.OperationType.QUERY)
    @GetMapping("/getFocal")
    public ResponseEntity getFocal(){
        try {
            List<String> dataRange = roleUtils.getCurrentUserUarBusinessDataRange();
            List<AdvancedSearchBean> data = itsApplicationService.getFocal(dataRange);
            return ResponseEntity.ok(
                    Map.of(
                            "success", true, "data", data
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.ok(
                    Map.of(
                            "success", false,
                            "message", e.getMessage()
                    )
            );
        }
    }

}
