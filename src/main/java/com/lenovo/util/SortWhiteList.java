package com.lenovo.util;


import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * @author: liufz
 * @date: 2023/5/5
 * @Description: 排序字段白名单
 */
public class SortWhiteList {
    /**
     * 一次性收集实体所有字段名
     * */
    public static Set<String> of(Class<?> clazz) {
        Set<String> set = new HashSet<>();
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                set.add(f.getName());
            }
        }
        return Set.copyOf(set);
    }

    /**
     * 校验：不通过直接抛出
     * 字段别名要用驼峰和实体对应
     * */
    public static void check(Set<String> whiteList, String field) {
        if (field == null || field.isEmpty()) {
            return;
        }
        if (!whiteList.contains(field)) {
            throw new IllegalArgumentException("非法排序字段: " + field);
        }
    }

}
