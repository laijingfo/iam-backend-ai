package com.lenovo.controller;


import com.cronutils.utils.StringUtils;
import com.lenovo.bean.DelegationBean;
import com.lenovo.config.LogOperation;
import com.lenovo.config.Logical;
import com.lenovo.config.RequiresPermission;
import com.lenovo.entity.Delegation;
import com.lenovo.service.*;
import com.lenovo.strategy.CheckStrategy;
import com.lenovo.strategy.SendEmailStrategyType;
import com.lenovo.strategy.factory.CheckStrategyFactory;
import com.lenovo.util.I18nUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.lenovo.security.utils.SecurityUtils;
import java.util.List;
import java.util.Map;
import static org.springframework.http.ResponseEntity.ok;

/**
 * @Description TODO 委托控制类
 * @author wangfenglong
 * @date 2026/4/20 16:47
**/
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/Delegation")
public class DelegationController
{
    private final DelegationService delegationService;
    private final CheckStrategyFactory strategyFactory;

    @GetMapping("/Delegation/getAll")
    @LogOperation(module = "我的委托", type = LogOperation.OperationType.QUERY, value = "查询所有授权信息")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "Line_Manager", "BPO"}, logical = Logical.OR, autoSetSsFlag = true)
    public ResponseEntity<?> query(@RequestParam(value = "page", defaultValue = "1") Integer page, @RequestParam(value = "size", defaultValue = "10") Integer size, DelegationBean delegationBean, String ssFlag)
    {
        String itCode = SecurityUtils.getCurrentUserId();
        log.info("我的委托：查询所有授权信息ssFlag={}", ssFlag);
        delegationBean.setRoleFlag(ssFlag);
        if ("2".equals(ssFlag))
        {
            delegationBean.setCurrentUserItCode(itCode);
        }
        if ("1".equals(ssFlag) || "2".equals(ssFlag) || "3".equals(ssFlag))
        {
            return ok(delegationService.query(delegationBean, page, size));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "isFinalDecision", false, "message", I18nUtil.get("delegation.error1")));
    }


    @PostMapping("/Delegation/insertDelegation")
    @LogOperation(module = "我的委托", type = LogOperation.OperationType.ADD, value = "新增授权信息")
    @RequiresPermission(roles = {"View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT", "Line_Manager", "BPO"}, logical = Logical.OR)
    public ResponseEntity<?> insertDelegation(@RequestBody DelegationBean delegationBean)
    {
        try
        {
            String scope = delegationBean.getDelegationScope();
            if(StringUtils.isEmpty(scope))
            {
                return ResponseEntity.ok(Map.of("success", false, "isFinalDecision", false, "message", I18nUtil.get("delegation.error2")));
            }

            if(StringUtils.isEmpty(delegationBean.getDelegationCmdbId()))
            {
                delegationBean.setDelegationCmdbId("ALL");
            }

            if(delegationService.insertDelegation(delegationBean) == 3)
            {
                return ResponseEntity.ok(Map.of("success", false, "isFinalDecision", false, "message", I18nUtil.get("delegation.error3")));
            }
            /*delegationService.sendDelegateeEmail(delegationBean);
            return ok("ok");*/
            CheckStrategy<DelegationBean,ResponseEntity<?>> strategy = strategyFactory.getStrategy(SendEmailStrategyType.DELEGATION_CYCLE_TYPE);
            return strategy.handle(null, delegationBean);

        }
        catch (Exception e)
        {
            log.error("新增授权信息失败：{}",e.getMessage(), e);
            return ResponseEntity.ok(Map.of("success", false, "isFinalDecision", false, "message", I18nUtil.get("common.fail") + " : " + e.getMessage()));
        }
    }

    /**
     * @Description TODO 删除授权信息
     * @author wangfenglong
     * @date 2026/5/7 14:18
    **/
    @DeleteMapping("/Delete/{id}")
    @LogOperation(module = "我的委托", type = LogOperation.OperationType.DELETE, value = "删除授权信息")
    public ResponseEntity<?> delete(@PathVariable Integer id)
    {
        //delegationService.removeById(id);
        Delegation delegation = new Delegation();
        delegation.setId(Long.valueOf(id));
        delegation.setDelete(Short.valueOf("1"));
        delegation.setUpdateBy(SecurityUtils.getCurrentUserId());
        delegationService.deleteDelegationByUpdateId(delegation);
        return ok(I18nUtil.get("common.success"));
    }

    /**
     * @Description TODO 查询需要授权的CMDB_ID信息
     * @author wangfenglong
     * @date 2026/5/7 14:18
    **/
    @GetMapping("/Delegation/getDelegationBpoData")
    @LogOperation(module = "我的委托", type = LogOperation.OperationType.QUERY, value = "查询需要授权的CMDB_ID信息")
    public List<DelegationBean> getCurrentItCodeInUserAccessReviewBpoData(DelegationBean delegationBean)
    {
        delegationBean.setBpo(delegationBean.getDelegator());
        delegationBean.setDelegationFlag("2");
        return delegationService.getCurrentItCodeInUserAccessReviewBpoData(delegationBean);
    }

}