package com.lenovo.service;

import com.lenovo.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/** 记录prod-na权限全量同步的最近成功日期，用于避免旧数据触发自动Resolved。 */
@Service
@RequiredArgsConstructor
public class NaSensitiveAccessSyncStatus {

    private static final String LAST_SUCCESS_DATE_KEY =
            "job:naSensitiveAccess:permissionSyncDate";

    private final RedisUtils redisUtils;

    public void markSuccessful() {
        boolean marked = redisUtils.set(
                LAST_SUCCESS_DATE_KEY,
                LocalDate.now().toString(),
                1,
                TimeUnit.DAYS
        );
        if (!marked) {
            throw new IllegalStateException("权限数据同步成功，但记录同步日期失败");
        }
    }

    public boolean isSuccessfulToday() {
        return LocalDate.now().toString().equals(String.valueOf(redisUtils.get(LAST_SUCCESS_DATE_KEY)));
    }
}
