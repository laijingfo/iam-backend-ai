package com.lenovo.ai.token.constant;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-13 10:00
 * @project iam-backend-ai
 * @description
 * 所有 token 相关 Redis key 的唯一构造入口
 *
 * 版本说明：
 *   成本 key 加 v2: 前缀，规避旧 String 数据（INCRBYFLOAT 写入）
 *   与新 Hash 数据（HINCRBY 写入）的类型冲突（WRONGTYPE）。
 *   旧 key 等待 TTL 自然过期。
 */
public final class TokenRedisKeys {

    private static final DateTimeFormatter DAY   = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    /** 成本字段名（Hash field），单位：微元（1 元 = 1_000_000 微元） */
    public static final String FIELD_COST = "cny";

    /** 微元换算率 */
    public static final long MICRO_CNY = 1_000_000L;

    private TokenRedisKeys() {}

    // ---- 用量（一直是 Hash，无需 v2） ----
    public static String usageDay(String userId)   { return "ai:token:usage:" + userId + ":" + LocalDate.now().format(DAY); }
    public static String usageMonth(String userId) { return "ai:token:usage:" + userId + ":" + LocalDate.now().format(MONTH); }

    // ---- 成本（★ 加 v2 前缀，避开旧 String 数据） ----
    public static String costDay(String userId)    { return "ai:token:cost:v2:" + userId + ":" + LocalDate.now().format(DAY); }
    public static String costMonth(String userId)  { return "ai:token:cost:v2:" + userId + ":" + LocalDate.now().format(MONTH); }

    // ---- 模型维度 ----
    public static String modelDay(String userId)   { return "ai:token:model:" + userId + ":" + LocalDate.now().format(DAY); }

    // ---- 明细 ----
    public static String recordsDay(String userId) { return "ai:token:records:" + userId + ":" + LocalDate.now().format(DAY); }

    // ---- 配额 ----
    public static String quota(String userId)      { return "ai:token:quota:" + userId; }
}