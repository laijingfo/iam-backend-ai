package com.lenovo.ai.token.constant;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 所有 token 相关 Redis key 的唯一构造入口。
 *
 * <p>★ P2 修复：所有 key 构造方法增加接受 {@link LocalDate} 的重载。
 * 原实现每次调用各自 {@code LocalDate.now()}，同一方法内多次调用理论上
 * 可能跨越午夜（如 23:59:59.999 → 00:00:00.001），导致 day/month key
 * 归属不同天。调用方应统一取一次 now 传入。</p>
 */
public final class TokenRedisKeys {

    private static final DateTimeFormatter DAY   = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    public static final String FIELD_COST = "cny";
    public static final long MICRO_CNY = 1_000_000L;

    private TokenRedisKeys() {}

    // ---- 用量 ----
    public static String usageDay(String userId) { return usageDay(userId, LocalDate.now()); }
    public static String usageDay(String userId, LocalDate day) {
        return "ai:token:usage:" + userId + ":" + day.format(DAY);
    }

    public static String usageMonth(String userId) { return usageMonth(userId, LocalDate.now()); }
    public static String usageMonth(String userId, LocalDate day) {
        return "ai:token:usage:" + userId + ":" + day.format(MONTH);
    }

    // ---- 成本（v2 前缀） ----
    public static String costDay(String userId) { return costDay(userId, LocalDate.now()); }
    public static String costDay(String userId, LocalDate day) {
        return "ai:token:cost:v2:" + userId + ":" + day.format(DAY);
    }

    public static String costMonth(String userId) { return costMonth(userId, LocalDate.now()); }
    public static String costMonth(String userId, LocalDate day) {
        return "ai:token:cost:v2:" + userId + ":" + day.format(MONTH);
    }

    // ---- 模型维度 ----
    public static String modelDay(String userId) { return modelDay(userId, LocalDate.now()); }
    public static String modelDay(String userId, LocalDate day) {
        return "ai:token:model:" + userId + ":" + day.format(DAY);
    }

    // ---- 明细 ----
    public static String recordsDay(String userId) { return recordsDay(userId, LocalDate.now()); }
    public static String recordsDay(String userId, LocalDate day) {
        return "ai:token:records:" + userId + ":" + day.format(DAY);
    }

    // ---- 配额 ----
    public static String quota(String userId) { return "ai:token:quota:" + userId; }
}