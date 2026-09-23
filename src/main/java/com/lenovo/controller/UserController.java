package com.lenovo.controller;

import com.lenovo.config.LogOperation;
import com.lenovo.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.lenovo.service.UserService;
import com.lenovo.entity.User;

import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController extends BaseController
{
    private final UserService userService;

    @GetMapping("/getAll")
    @LogOperation(module = "用户管理", type = LogOperation.OperationType.QUERY, value = "查询所有用户信息")
    public ResponseEntity getAll(String name, String type, @RequestParam(required = false, defaultValue = "1") int page, @RequestParam(required = false, defaultValue = "100") int size)
    {
        return ok(userService.queryAll(name, type, page, size));
    }

    @GetMapping("/byRole")
    @LogOperation(module = "用户管理", type = LogOperation.OperationType.QUERY, value = "根据Role查询用户信息")
    public ResponseEntity queryByRole(@RequestParam(required = false) String name, @RequestParam(required = false) Integer workspaceId, String roleName, @RequestParam(required = false, defaultValue = "1") int page,
                                      @RequestParam(required = false, defaultValue = "100") int size)
    {
        return ok(userService.queryByRole(name, roleName, workspaceId, page, size));
    }

    @PutMapping("/{itcode}/roles")
    @LogOperation(module = "用户管理", type = LogOperation.OperationType.UPDATE, value = "修改/新增/删除用户信息")
    public ResponseEntity modifyById(@PathVariable String itcode, @RequestBody Set<Long> roleIds)
    {
        User user = new User();
        user.setId(itcode);
        user.setUserName(itcode);
        //user.setRoles(roleIds);
        user.setRoleSet(roleIds);
        user.setCurrentLoggedUserItCode(SecurityUtils.getCurrentUserId());
        userService.modifyAdfsUserRole(user);
        return ok("ok");
    }

    @GetMapping("/{itcode}")
    @LogOperation(module = "用户管理", type = LogOperation.OperationType.QUERY, value = "当前登陆用户信息")
    public ResponseEntity getOne(@PathVariable String itcode)
    {
        return ok(userService.getAdfsUser(itcode));
    }

}
