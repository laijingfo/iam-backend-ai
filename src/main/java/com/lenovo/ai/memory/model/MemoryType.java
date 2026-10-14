package com.lenovo.ai.memory.model;

import java.util.Locale;
import java.util.Set;

/**
 * 长期记忆类型。
 * <p>Step 5 启用后，MemoryItem.type 可改为本枚举。</p>
 */
public enum MemoryType {
    PREFERENCE, FACT, ENTITY, QUESTION;

    public static final Set<String> ALLOWED = Set.of(
            PREFERENCE.name(), FACT.name(), ENTITY.name(), QUESTION.name());

    public static MemoryType of(String s) {
        return valueOf(s.trim().toUpperCase(Locale.ROOT));
    }

    public static boolean isValid(String s) {
        return s != null && ALLOWED.contains(s.trim().toUpperCase(Locale.ROOT));
    }
}