package com.lenovo.controller;

import com.lenovo.config.LogOperation;
import com.lenovo.config.Logical;
import com.lenovo.config.RequiresPermission;
import com.lenovo.entity.Role;
import com.lenovo.entity.User;
import com.lenovo.service.RoleService;
import com.lenovo.service.UserService;
import com.lenovo.util.I18nUtil;
import com.lenovo.util.ValidUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * 角色API
 *
 * @author iyesking
 * @date 2023-02-09
 */
@RestController
@RequestMapping("/role")
public class RoleController extends BaseController {

    private final Logger log = LoggerFactory.getLogger(RoleController.class);

    @Autowired
    private RoleService roleService;

    private @Autowired
    UserService userService;



    @GetMapping("/{id}")
    public ResponseEntity getOne(@PathVariable String id) {
        return ok(roleService.getOne(id));
    }

    @GetMapping("/getDetailMenuTree/{roleId}")
    public ResponseEntity findMenuByRoleId(@PathVariable String roleId) {
        return ok(roleService.findMenuByRoleIdAndCheckTrue(roleId));
    }

    @GetMapping("/getDetailUserListByRole/{roleId}")
    public ResponseEntity findUserListByRole(@PathVariable String roleId) {
        return ok(roleService.findUserListByRole(roleId));
    }

    @GetMapping("/detail/{roleId}")
    @LogOperation(module = "角色管理", type = LogOperation.OperationType.QUERY, value = "新增角色_查询所有菜单和权限")
    public ResponseEntity detail(@PathVariable String roleId)
    {
        return ok(roleService.detail(roleId));
    }


    @GetMapping("/page")
    @LogOperation(module = "角色管理", type = LogOperation.OperationType.QUERY, value = "角色管理页面")
    public ResponseEntity selectByPage(@RequestParam(required = false, defaultValue = "1") Integer currentPage, @RequestParam(required = false, defaultValue = "20") Integer pageSize, Role role)
    {
        return ok(roleService.selectByPage(currentPage, pageSize, role));
    }

    @GetMapping("/getAll")
    public ResponseEntity findPage() {
        return ok(roleService.queryAll());
    }

    @DeleteMapping("/{id}")
    @LogOperation(module = "角色管理", type = LogOperation.OperationType.QUERY, value = "删除当前角色")
    public ResponseEntity deleteById(@PathVariable String id)
    {
        String flag = roleService.deleteById(id);
        //return ok("ok");
        if("1".equals(flag))
        {
            //return new ResponseEntity(flag,HttpStatus.OK);
            return ResponseEntity.ok().body(Map.of
            (
            "success", false,
            "flag", "1",
            "message", I18nUtil.get("role.error1")
            ));
        }
        if("2".equals(flag))
        {
            //return new ResponseEntity(flag,HttpStatus.OK);
            return ResponseEntity.ok().body(Map.of
            (
            "success", false,
            "flag", "2",
            "message", I18nUtil.get("role.error2")
            ));
        }
        return ok("ok");
    }

    @GetMapping("/getADFSUserItem")
    public ResponseEntity getADFSUserItem(String name) {
        return ok(userService.getADFSUserItem(name));
    }

    @GetMapping("/getServiceCategoryL2Names")
    public ResponseEntity<?> getServiceCategoryL2Names(String name) {
        return ok();
    }

    @PostMapping
    @LogOperation(module = "角色管理", type = LogOperation.OperationType.QUERY, value = "新增角色")
    @RequiresPermission(roles = {"UAR_Admin_IT", "UAR_System_Admin_IT"}, logical = Logical.OR)
    public ResponseEntity<?> add(@RequestBody @Validated Role role, BindingResult result)
    {
        List<FieldError> fieldErrors = result.getFieldErrors();
        if (!fieldErrors.isEmpty()) {
            return ResponseEntity.ok(Map.of( "success", false, "message", fieldErrors.get(0).getDefaultMessage()));
        }

        String roleName = role.getName();

        if (!ValidUtils.isUserName(roleName)) {
            return ResponseEntity.ok(Map.of( "success", false, "message", "wrongNameFormat"));
        }

        Role roName = roleService.getName(roleName, null, role.getWorkspaceId());
        if (Objects.nonNull(roName)) {
            return ResponseEntity.ok(Map.of( "success", false, "message", "repeatNames"));
        }

        roleService.create(role);
        return ok(role);
    }

    @PutMapping
    @LogOperation(module = "角色管理", type = LogOperation.OperationType.QUERY, value = "角色管理-修改角色")
    @RequiresPermission(roles = {"UAR_Admin_IT", "UAR_System_Admin_IT"}, logical = Logical.OR)
    public ResponseEntity<?> modifyById(@RequestBody @Validated Role role, BindingResult result)
    {
        List<FieldError> fieldErrors = result.getFieldErrors();
        String roleName = role.getName();
        if (!fieldErrors.isEmpty())
        {
            return ResponseEntity.ok(Map.of( "success", false, "message", fieldErrors.get(0).getDefaultMessage()));

        }
        if (!ValidUtils.isUserName(roleName))
        {
            return ResponseEntity.ok(Map.of( "success", false, "message", "wrongNameFormat"));

        }
        if (Objects.isNull(role.getId()))
        {
            return ResponseEntity.ok(Map.of( "success", false, "message", "Primary key cannot be empty!"));
        }
        Role roName = roleService.getName(roleName, String.valueOf(role.getId()), role.getWorkspaceId());
        if (Objects.nonNull(roName))
        {
            return ResponseEntity.ok(Map.of( "success", false, "message", "repeatNames"));
        }
        roleService.modifyById(role);
        return ok(role);
    }

    @PutMapping("/resetStatus")
    public ResponseEntity resetStatus(@RequestBody User user) {
        String roleId = user.getId();
        roleService.resetStatus(roleId);
        return ok("ok");


    }

    @GetMapping("/findUserMenuByRoleId")
    public ResponseEntity findUserMenuByRoleId(String roleId) {
        return ok(roleService.findUserMenuByRoleId(roleId));
    }


}