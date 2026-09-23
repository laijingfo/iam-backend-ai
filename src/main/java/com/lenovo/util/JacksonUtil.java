package com.lenovo.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Collections;
import java.util.List;

public class JacksonUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            // 下划线转驼峰
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            // 兼容 PostgreSQL 时间格式
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    public static <T> List<T> toList(String json, Class<T> clazz) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return Collections.emptyList();
        }
        try {
            System.out.println(json);
            return MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            throw new RuntimeException("JSON 转 List 失败", e);
        }
    }
}
