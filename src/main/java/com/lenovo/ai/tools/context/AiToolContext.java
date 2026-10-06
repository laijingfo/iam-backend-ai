package com.lenovo.ai.tools.context;

import com.lenovo.security.utils.RoleUtils;
import com.lenovo.util.RedisUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AiToolContext {

    private static final ThreadLocal<AiToolContext> HOLDER = new ThreadLocal<>();

    private final String userId;
    private final RoleUtils roles;
    private final RedisUtils redisUtils;
    private final Set<String> roleSet;

    private AiToolContext(String userId, RoleUtils roles, RedisUtils redisUtils) {
        this.userId = userId;
        this.roles = roles;
        this.redisUtils = redisUtils;
        this.roleSet = captureRoles(roles);
    }

    public static AiToolContext capture(String userId, RoleUtils roles, RedisUtils redisUtils) {
        return new AiToolContext(userId, roles, redisUtils);
    }

    /** 兼容旧调用：手工传入角色集合 */
    public static AiToolContext capture(String userId, Set<String> roleSet, RedisUtils redisUtils) {
        AiToolContext ctx = new AiToolContext(userId, null, redisUtils);
        ctx.roleSet.clear();
        if (roleSet != null) ctx.roleSet.addAll(roleSet);
        return ctx;
    }

    private static Set<String> captureRoles(RoleUtils roles) {
        // TODO 根据 RoleUtils 的实际 API 调整；下面只是常见形态
        // 例如 roles.getRoles() / roles.list() / roles.current()
        if (roles == null) return new HashSet<>();
        try {
            Object v = roles.getClass().getMethod("getRoles").invoke(roles);
            if (v instanceof Set<?> s) {
                Set<String> out = new HashSet<>();
                for (Object o : s) if (o != null) out.add(String.valueOf(o));
                return out;
            }
        } catch (Exception ignore) {}
        return new HashSet<>();
    }

    public static void set(AiToolContext context) { HOLDER.set(context); }
    public static AiToolContext get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }

    public String getUserId() { return userId; }
    public RoleUtils getRoles() { return roles; }
    public RedisUtils getRedisUtils() { return redisUtils; }

    public boolean hasRole(String role) {
        if (role == null) return false;
        if ("*".equals(role)) return true;
        return roleSet.contains(role);
    }

    public Set<String> roleSet() { return Collections.unmodifiableSet(roleSet); }
}