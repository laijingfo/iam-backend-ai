package com.lenovo.util;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @Description TODO UUID v7 生成工具类 生成标准 RFC 9562 UUID v7（Java 8 可用）
 * @ClassName UUIDv7
 * @Author wangfenglong
 * @Date 2026/4/21 16:12
 **/
public class UUIDv7
{
    public static UUID random()
    {
        long timestamp = System.currentTimeMillis();

        // 生成 10 字节随机数
        byte[] randomBytes = new byte[10];
        ThreadLocalRandom.current().nextBytes(randomBytes);

        // 构造高位：时间戳前 48 位 + 版本 7
        long msb = (timestamp << 16) | ((randomBytes[0] & 0xFFL) << 8) | (randomBytes[1] & 0xFFL);
        msb &= ~0xF000L; // 清空版本位
        msb |= 0x7000L;  // 设置版本 = 7

        // 构造低位：变体 RFC4122 + 随机数
        long lsb = ((randomBytes[2] & 0x3FL) << 56)
                | ((randomBytes[3] & 0xFFL) << 48)
                | ((randomBytes[4] & 0xFFL) << 40)
                | ((randomBytes[5] & 0xFFL) << 32)
                | ((randomBytes[6] & 0xFFL) << 24)
                | ((randomBytes[7] & 0xFFL) << 16)
                | ((randomBytes[8] & 0xFFL) << 8)
                | (randomBytes[9] & 0xFFL);
        lsb &= ~0xC000000000000000L;
        lsb |= 0x8000000000000000L;
        return new UUID(msb, lsb);
    }

}
