package com.lenovo.ai.tools.context;

import com.lenovo.entity.Role;
import com.lenovo.config.GlobalBusinessStatusEnum;
import com.lenovo.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具执行上下文：携带当前用户 ID 和角色集合，供工具做权限判断。
 * <p>
 * 角色从 Redis 直接读取（与 PermissionAspect / RoleUtils 同一来源），
 * 避免反射调用 RoleUtils.getRoles()。
 */
@Slf4j
public class AiToolContext {

    private static final ThreadLocal<AiToolContext> HOLDER = new ThreadLocal<>();

    private final String userId;
    private final RedisUtils redisUtils;
    private final Set<String> roleSet;

    private AiToolContext(String userId, RedisUtils redisUtils, Set<String> roleSet) {
        this.userId = userId;
        this.redisUtils = redisUtils;
        this.roleSet = roleSet;
    }

    /**
     * 从 Redis 读取当前用户的真实角色列表，构造 AiToolContext。
     */
    public static AiToolContext capture(String userId, RedisUtils redisUtils) {
        Set<String> roles = loadRolesFromRedis(userId, redisUtils);
        return new AiToolContext(userId, redisUtils, roles);
    }

    /**
     * 兼容旧调用：手工传入角色集合（用于测试或特殊场景）。
     */
    public static AiToolContext capture(String userId, Set<String> roleSet, RedisUtils redisUtils) {
        Set<String> roles = roleSet != null ? new HashSet<>(roleSet) : new HashSet<>();
        return new AiToolContext(userId, redisUtils, roles);
    }

    private static Set<String> loadRolesFromRedis(String userId, RedisUtils redisUtils) {
        if (userId == null || redisUtils == null) return new HashSet<>();
        try {
            String redisKey = userId + GlobalBusinessStatusEnum.REDIS_KEY_USER_ROLE.desc;
            Object raw = redisUtils.hget(redisKey, userId);
            if (raw instanceof Role roleContainer) {
                List<Role> roleList = roleContainer.getRoleList();
                if (roleList != null && !roleList.isEmpty()) {
                    return roleList.stream()
                            .map(Role::getName)
                            .filter(name -> name != null && !name.isBlank())
                            .collect(Collectors.toSet());
                }
            }
        } catch (Exception e) {
            log.warn("从 Redis 读取用户角色失败: userId={}", userId, e);
        }
        return new HashSet<>();
    }

    public static void set(AiToolContext context) { HOLDER.set(context); }
    public static AiToolContext get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }

    public String getUserId() { return userId; }
    public RedisUtils getRedisUtils() { return redisUtils; }

    public boolean hasRole(String role) {
        if (role == null) return false;
        if ("*".equals(role)) return true;
        return roleSet.contains(role);
    }

    public Set<String> roleSet() { return Collections.unmodifiableSet(roleSet); }
}