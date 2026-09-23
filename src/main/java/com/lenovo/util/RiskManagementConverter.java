package com.lenovo.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.baomidou.mybatisplus.annotation.TableField;
import com.lenovo.entity.RiskManagement;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RiskManagementConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        // 配置 Jackson 使用下划线命名策略并忽略 null 值
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public static String convertToJson(List<RiskManagement> riskManagementList,String index,String source,String host) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (RiskManagement rm : riskManagementList) {
            Map<String, Object> eventMap = new LinkedHashMap<>();

            // 生成 event 字符串
            String eventString = generateEventString(rm);
            eventMap.put("event", eventString);

            // 固定字段
            eventMap.put("index", index);
            eventMap.put("source", source);
            eventMap.put("host", host);

            // 时间戳处理（示例使用 updateTime）
            long timestamp = rm.getUpdateTime().atZone(ZoneId.systemDefault()).toEpochSecond();
            eventMap.put("time", timestamp);

            result.add(eventMap);
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 转换失败", e);
        }
    }

    private static String generateEventString(RiskManagement rm) {
        Map<String, String> fieldMap = new LinkedHashMap<>();

        // 反射获取所有字段
        for (Field field : RiskManagement.class.getDeclaredFields()) {
            // 跳过静态字段和 @TableField(exist = false) 的字段
            if (Modifier.isStatic(field.getModifiers())) continue;
            TableField tableField = field.getAnnotation(TableField.class);
            if (tableField != null && !tableField.exist()) continue;

            try {
                field.setAccessible(true);
                Object value = field.get(rm);
                String fieldName = camelToSnake(field.getName());
                fieldMap.put(fieldName, formatValue(value));
            } catch (IllegalAccessException e) {
                throw new RuntimeException("字段访问失败: " + field.getName(), e);
            }
        }

        // 构建 event 字符串
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : fieldMap.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static String camelToSnake(String str) {
        return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private static String formatValue(Object value) {
        if (value == null) return "null";

        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } else if (value instanceof LocalDate) {
            return ((LocalDate) value).format(DateTimeFormatter.ISO_LOCAL_DATE);
        } else if (value instanceof Boolean) {
            return ((Boolean) value) ? "true" : "false";
        }
        return value.toString();
    }
}
