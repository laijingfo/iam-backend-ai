package com.lenovo.security.utils;

import cn.hutool.core.collection.CollectionUtil;
import com.lenovo.config.GlobalBusinessStatusEnum;
import com.lenovo.entity.Role;
import com.lenovo.service.DelegationService;
import com.lenovo.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Component
@RequiredArgsConstructor
public class RoleUtils
{
    private final RedisUtils redisUtils;
    private final DelegationService delegationService;

    /**
     * 管理员角色集合
     */
    public static final List<String> ADMIN = List.of(
            "View_Only_IT",
            "UAR_Admin_IT",
            "UAR_System_Admin_IT",
            "平台管理员"
    );

    /**
     * 业务角色集合
     */
    public static final List<String> BUSINESS = List.of(
            "Line_Manager",
            "BPO",
            "Access_User"
    );

    /**
     * 处理人角色
     */
    public static final String PROCESSER = "UAR_Processer";
    public static final String OPERATION_OWNER_FOCAL = "Operation_owner_focal";

    public static final String ROLE_READ_PERMISSION = "read";
    public static final String ROLE_WRITE_PERMISSION = "write";

    //@Autowired
    //public RedisUtils redisUtils;

    /**
     * 获取当前用户数据权限范围:line_manager
     *
     * @return ["*"] or [itCode]
     */
    public List<String> getCurrentUserBusinessDataRangeOfMgr(String permission) {
        return getCurrentUserBusinessDataRangeOfMgr(permission, true);
    }

    /**
     * 获取当前用户个人任务的数据权限范围:line_manager。
     * 管理员在个人任务场景中不放大为全量权限，但仍保留委托数据。
     */
    public List<String> getCurrentUserPersonalBusinessDataRangeOfMgr() {
        return getCurrentUserBusinessDataRangeOfMgr(ROLE_READ_PERMISSION, false);
    }

    private List<String> getCurrentUserBusinessDataRangeOfMgr(String permission, boolean enableAdminFullAccess) {
        String itCode = SecurityUtils.getCurrentUserId();
        //从redis中获取当前登陆账户的角色列表
        Role roleContainList = (Role) redisUtils.hget(itCode + GlobalBusinessStatusEnum.REDIS_KEY_USER_ROLE.desc, itCode);
        if (Objects.isNull(roleContainList) || CollectionUtil.isEmpty(roleContainList.getRoleList()))//redis中没有存储用户角色信息，所以查全部信息
        {
            throw new RuntimeException("Redis中不存在当前用户信息");
        }

        // 限制数据权限读写权限
        if (enableAdminFullAccess && permission.equals(ROLE_READ_PERMISSION)) {
            // 如果是[管理员角色集合]就不用限制数据权限,
            for (Role role : roleContainList.getRoleList()) {
                if (ADMIN.contains(role.getName())) {
                    return List.of("*");
                }
            }
        }

        List<String> roleList = new ArrayList<>();
        // 先把自己添加到返回列表中
        roleList.add(itCode);

        // 获取被授权的角色 (Delegator)
        for (Role role : roleContainList.getRoleList()) {
            if (GlobalBusinessStatusEnum.Line_Manager.desc.equals(role.getName())) {
                if (StringUtils.isNotEmpty(role.getDelegator())) {
                    roleList.addAll(
                            Arrays.asList(role.getDelegator().split(","))
                    );
                }
            }
        }

        return roleList;
    }

    /**
     * 获取当前用户数据权限范围:bpo
     *
     * @return ["*"] or [itCode]
     */
    public Map<String, List<String>> getCurrentUserBusinessDataRangeOfBpo(String permission) {
        return getCurrentUserBusinessDataRangeOfBpo(permission, true);
    }

    /**
     * 获取当前用户个人任务的数据权限范围:bpo。
     * 管理员在个人任务场景中不放大为全量权限，但仍保留委托数据。
     */
    public Map<String, List<String>> getCurrentUserPersonalBusinessDataRangeOfBpo() {
        return getCurrentUserBusinessDataRangeOfBpo(ROLE_READ_PERMISSION, false);
    }

    private Map<String, List<String>> getCurrentUserBusinessDataRangeOfBpo(String permission, boolean enableAdminFullAccess) {
        String itCode = SecurityUtils.getCurrentUserId();
        //从redis中获取当前登陆账户的角色列表
        Role roleContainList = (Role) redisUtils.hget(itCode + GlobalBusinessStatusEnum.REDIS_KEY_USER_ROLE.desc, itCode);
        if (Objects.isNull(roleContainList) || CollectionUtil.isEmpty(roleContainList.getRoleList()))//redis中没有存储用户角色信息，所以查全部信息
        {
            throw new RuntimeException("Redis中不存在当前用户信息");
        }

        // default value
        List<String> all = Collections.singletonList("ALL");

        // 限制数据权限读写权限
        if (enableAdminFullAccess && permission.equals(ROLE_READ_PERMISSION)) {
            // 如果是[管理员角色集合]就不用限制数据权限
            for (Role role : roleContainList.getRoleList()) {
                if (ADMIN.contains(role.getName())) {
                    return Map.of("*", all);
                }
            }
        }

        Map<String, List<String>> delegationMap = new HashMap<>();
        // 先把自己添加到返回列表中
        delegationMap.put(itCode, all);

        // 获取被授权的角色 (Delegator)
        for (Role role : roleContainList.getRoleList()) {
            if (GlobalBusinessStatusEnum.BPO.desc.equals(role.getName())) {
                Map<String, String> delegationCmdbIdList = delegationService.getDelegationCmdbIdList(itCode);
                if (delegationCmdbIdList == null ||delegationCmdbIdList.isEmpty()) {
                    // 没有授权，返回，保证自身数据查询逻辑正常执行
                    return delegationMap;
                }
                // 处理类型 String转List 存入delegationMap
                delegationCmdbIdList.forEach((k, v) -> {
                    delegationMap.put(k, Arrays.asList(v.split(",")));
                });
            }
        }
        Set<Map.Entry<String, List<String>>> entries = delegationMap.entrySet();
        for (Map.Entry<String, List<String>> entry : entries) {
            delegationMap.put(entry.getKey(), entry.getValue().stream().distinct().collect(Collectors.toList()));
        }
        return delegationMap;
    }

    /**
     * 获取当前用户UAR业务数据权限范围
     *
     * @return 管理员返回 ["*"]，其他用户返回其负责的 CMDB ID 列表
     */
    public List<String> getCurrentUserUarBusinessDataRange() {
        String itCode = SecurityUtils.getCurrentUserId();
        //从redis中获取当前登陆账户的角色列表
        Role roleContainList = (Role) redisUtils.hget(itCode + GlobalBusinessStatusEnum.REDIS_KEY_USER_ROLE.desc, itCode);
        if (Objects.isNull(roleContainList) || CollectionUtil.isEmpty(roleContainList.getRoleList()))//redis中没有存储用户角色信息，所以查全部信息
        {
            throw new RuntimeException("Redis中不存在当前用户信息");
        }

        // 如果是[管理员角色集合]就不用限制数据权限
        for (Role role : roleContainList.getRoleList()) {
            if (ADMIN.contains(role.getName())) {
                return List.of("*");
            }
        }


        List<String> allCmdbIds = new ArrayList<>();

        // 检查当前用户是否拥有 UAR_Processer
        for (Role role : roleContainList.getRoleList()) {
            if (PROCESSER.equals(role.getName())) {
                List<String> processerCmdbIds = Optional.of(role)
                        .map(Role::getCmdbIdOfUarProcesser)
                        .map(s -> s.split(","))
                        .map(Arrays::asList)
                        .orElse(List.of());

                allCmdbIds.addAll(processerCmdbIds);
            }

            // 检查当前用户是否拥有 Operation_Owner_Focal
            if (OPERATION_OWNER_FOCAL.equals(role.getName())) {
                List<String> focalCmdbIds = Optional.of(role)
                        .map(Role::getCmdbIdOfOperationOwnerFocalRole)
                        .map(s -> s.split(","))
                        .map(Arrays::asList)
                        .orElse(List.of());

                allCmdbIds.addAll(focalCmdbIds);
            }
        }

        return allCmdbIds.isEmpty() ? List.of("None") : allCmdbIds;

    }

}
