package com.lenovo.security.aspect;

import cn.hutool.core.collection.CollectionUtil;
import com.lenovo.config.GlobalBusinessStatusEnum;
import com.lenovo.entity.Role;
import com.lenovo.security.annotation.DenyEditRole;
import com.lenovo.security.exception.AuthException;
import com.lenovo.security.exception.BadRequestException;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.util.RedisUtils;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Aspect
@Component
public class DenyEditRolePermissionInterceptor {

    @Autowired
    public RedisUtils redisUtils;

    @Around("@annotation(denyEditRole)")
    public Object checkRolePermission(ProceedingJoinPoint joinPoint, DenyEditRole denyEditRole) throws Throwable {
        // 获取当前用户角色
        String itCode = SecurityUtils.getCurrentUserId();
        //从redis中获取当前登陆账户的角色列表
        Role roleContainList = (Role) redisUtils.hget(itCode + GlobalBusinessStatusEnum.REDIS_KEY_USER_ROLE.desc, itCode);
        if (Objects.isNull(roleContainList) || CollectionUtil.isEmpty(roleContainList.getRoleList()))//redis中没有存储用户角色信息，所以查全部信息
        {
            throw new RuntimeException("Redis中不存在当前用户信息");
        }
        Set<String> userRoles = roleContainList.getRoleList()
                .stream()
                .map(Role::getName)          // 取 name
                .filter(Objects::nonNull)    // 去 null
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());

        if (userRoles == null || userRoles.isEmpty()) {
            throw new AuthException("用户未登录或无角色信息");
        }

        // 获取注解中配置的被禁止的角色
        Set<String> deniedRoles = Arrays.stream(denyEditRole.roles())
                .collect(Collectors.toSet());

        // 检查是否匹配禁止的角色
        boolean hasDeniedRole;

        if (denyEditRole.matchAll()) {
            // 需要所有禁止的角色都匹配
            hasDeniedRole = userRoles.containsAll(deniedRoles);
        } else {
            // 任一禁止的角色匹配即可
            hasDeniedRole = userRoles.stream().anyMatch(deniedRoles::contains);
        }

        if (hasDeniedRole) {
            throw new BadRequestException(HttpStatus.FORBIDDEN, "当前角色禁止访问此接口");
        }

        // 继续执行原方法
        return joinPoint.proceed();
    }

}
