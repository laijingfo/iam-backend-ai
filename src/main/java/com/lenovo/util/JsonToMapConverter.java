package com.lenovo.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JsonToMapConverter {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将 JSON 数组字符串转换为包含 Map 的 List
     */
    public static List<Map<String, String>> parseJsonArray(String json) {
        try {
            List<Map<String, Object>> rawList = objectMapper.readValue(
                    json,
                    new TypeReference<List<Map<String, Object>>>() {}
            );

            return rawList.stream()
                    .map(JsonToMapConverter::convertMapValuesToString)
                    .collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            System.err.println("JSON 解析错误: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 将单个 JSON 对象字符串转换为 Map
     */
    public static Map<String, String> parseJsonObject(String json) {
        try {
            Map<String, Object> rawMap = objectMapper.readValue(
                    json,
                    new TypeReference<Map<String, Object>>() {}
            );

            return convertMapValuesToString(rawMap);
        } catch (JsonProcessingException e) {
            System.err.println("JSON 解析错误: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 递归转换 Map 中所有值为 String 类型
     */
    private static Map<String, String> convertMapValuesToString(Map<String, Object> rawMap) {
        return rawMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> convertValueToString(entry.getValue()),
                        (existing, replacement) -> existing,
                        HashMap::new
                ));
    }

    /**
     * 类型安全的值转换
     */
    private static String convertValueToString(Object value) {
        if (value == null) return "";
        if (value instanceof String) return (String) value;
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Map) return convertMapValuesToString((Map) value).toString();
        if (value instanceof List) return convertListValuesToString((List) value).toString();
        return value.toString();
    }

    /**
     * 处理嵌套列表
     */
    private static List<String> convertListValuesToString(List<?> list) {
        return list.stream()
                .map(JsonToMapConverter::convertValueToString)
                .collect(Collectors.toList());
    }
}