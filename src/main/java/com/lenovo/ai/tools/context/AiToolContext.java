package com.lenovo.ai.tools.context;

import com.lenovo.security.utils.RoleUtils;
import com.lenovo.util.RedisUtils;

public class AiToolContext {

    private static final ThreadLocal<AiToolContext> HOLDER = new ThreadLocal<>();

    private final String userId;
    private final RoleUtils roles;
    private final RedisUtils redisUtils;

    private AiToolContext(String userId, RoleUtils roles, RedisUtils redisUtils) {
        this.userId = userId;
        this.roles = roles;
        this.redisUtils = redisUtils;
    }

    public static AiToolContext capture(String userId, RoleUtils roles, RedisUtils redisUtils) {
        return new AiToolContext(userId, roles, redisUtils);
    }

    public static void set(AiToolContext context) {
        HOLDER.set(context);
    }

    public static AiToolContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public String getUserId() {
        return userId;
    }

    public RoleUtils getRoles() {
        return roles;
    }

    public RedisUtils getRedisUtils() {
        return redisUtils;
    }
}