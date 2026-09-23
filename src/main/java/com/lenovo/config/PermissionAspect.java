package com.lenovo.config;

import cn.hutool.core.collection.CollectionUtil;
import com.lenovo.entity.Role;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @Description 权限校验切面
 * @ClassName PermissionAspect
 * @Author wangfenglong
 * @Date 2026/4/20 14:24
 **/
@Aspect
@Component
@Order(2)
@Slf4j
@RequiredArgsConstructor
public class PermissionAspect
{
    private final RedisUtils redisUtils;

    @Pointcut("@annotation(com.lenovo.config.RequiresPermission)")
    public void permissionPointcut() {}

    @Around("permissionPointcut()")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable
    {
        try
        {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method targetMethod = signature.getMethod();
            RequiresPermission permission = targetMethod.getAnnotation(RequiresPermission.class);
            
            if (permission == null)
            {
                return joinPoint.proceed();
            }

            String[] roles = permission.roles();
            String[] permissions = permission.permissions();
            Logical logical = permission.logical();
            boolean ignoreSuperAdmin = permission.ignoreSuperAdmin();

            //获取当前用户信息
            String itCode = SecurityUtils.getCurrentUserId();
            if (itCode == null || itCode.isEmpty())
            {
                log.error("权限校验失败：无法获取当前用户ID");
                //return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
                return ResponseEntity.ok(Map.of( "success", false, "message", "😂权限校验失败：无法获取当前用户itCode"));
            }

            //从Redis获取用户角色
            Role roleContainList = (Role) redisUtils.hget(itCode + GlobalBusinessStatusEnum.REDIS_KEY_USER_ROLE.desc, itCode);
            if (Objects.isNull(roleContainList) || CollectionUtil.isEmpty(roleContainList.getRoleList()))
            {
                log.warn("权限校验失败：Redis中不存在用户角色信息, itCode={}", itCode);
                //return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null);
                return ResponseEntity.ok(Map.of( "success", false, "message", "😂权限校验失败：Redis中不存在用户角色信息,, itCode=" + itCode));
            }

            //提取用户角色名称列表
            List<String> userRoleNames = roleContainList.getRoleList().stream().map(Role::getName).distinct().collect(Collectors.toList());

            if (roles != null && roles.length > 0)
            {
                boolean hasRole = checkRoles(userRoleNames, roles, logical);
                if (!hasRole)
                {
                    log.warn("权限校验失败：用户 {} 不包含所需角色, 用户角色={}, 需要角色={}", itCode, userRoleNames, roles);
                    //return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
                    return ResponseEntity.ok(Map.of( "success", false, "message", "😂权限校验失败：用户: " + itCode + " ,不包含所需角色, 用户角色=" + userRoleNames + ", 需要角色=" + Arrays.toString(roles)));
                }
            }

            //权限码校验（如果后续需要实现）
            if (permissions != null && permissions.length > 0)
            {
                // TODO: 实现权限码校验逻辑
                log.info("权限码校验暂未实现");
            }

            log.info("权限校验成功：用户 {} 通过校验", itCode);
            
            // 检查是否需要自动设置ssFlag参数
            Object[] args = joinPoint.getArgs();
            if (permission.autoSetSsFlag())
            {
                // 判断用户角色并设置对应的ssFlag值
                List<String> itRoles = List.of("View_Only_IT", "UAR_Admin_IT", "UAR_System_Admin_IT");
                List<String> lmAndBpoRoles = List.of("Line_Manager", "BPO");
                boolean hasItRole = userRoleNames.stream().anyMatch(itRoles::contains);
                boolean hasLmOrBpoRole = userRoleNames.stream().anyMatch(lmAndBpoRoles::contains);
                String ssFlagValue;
                if (hasItRole)
                {
                    ssFlagValue = "1";
                }
                else if (hasLmOrBpoRole)
                {
                    ssFlagValue = "2";
                }
                else
                {
                    ssFlagValue = "3";
                }
                
                // 查找ssFlag参数并设置对应的值
                String[] parameterNames = signature.getParameterNames();
                for (int i = 0; i < parameterNames.length; i++)
                {
                    if ("ssFlag".equals(parameterNames[i]))
                    {
                        args[i] = ssFlagValue;
                        log.info("自动设置ssFlag参数为{}，用户 {} 的角色={}", ssFlagValue, itCode, userRoleNames);
                        break;
                    }
                }
            }
            
            return joinPoint.proceed(args);
        }
        catch (Throwable e)
        {
            log.error("权限校验过程发生异常", e);
            // 发生异常时返回500错误
            //return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            return ResponseEntity.ok(Map.of( "success", false, "message", "😂权限校验过程发生异常:" + e.getMessage()));
        }
    }

    /**
     * 校验用户角色是否满足要求
     * @param userRoleNames 用户拥有的角色列表
     * @param requiredRoles 需要的角色列表
     * @param logical 校验逻辑（AND/OR）
     * @return 是否通过校验
     */
    private boolean checkRoles(List<String> userRoleNames, String[] requiredRoles, Logical logical)
    {
        if (logical == Logical.AND)
        {
            // AND逻辑：用户必须拥有所有需要的角色
            for (String role : requiredRoles)
            {
                if (!userRoleNames.contains(role))
                {
                    return false;
                }
            }
            return true;
        }
        else
        {
            // OR逻辑：用户拥有任一角色即可
            for (String role : requiredRoles)
            {
                if (userRoleNames.contains(role))
                {
                    return true;
                }
            }
            return false;
        }
    }
}
