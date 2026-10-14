package com.lenovo.ai.memory.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-13
 * @project iam-backend-ai
 * @description
 * 向量工具
 */
public final class VectorUtils {

    private VectorUtils() {}

    /** List<Double> -> float32 小端字节 */
    public static byte[] toFloatBytes(List<Double> vec) {
        if (vec == null || vec.isEmpty()) return new byte[0];
        ByteBuffer buf = ByteBuffer.allocate(vec.size() * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (Double d : vec) buf.putFloat(d == null ? 0f : d.floatValue());
        return buf.array();
    }

    /** 余弦相似度 */
    public static double cosine(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.size() != b.size() || a.isEmpty()) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i) == null ? 0 : a.get(i);
            double y = b.get(i) == null ? 0 : b.get(i);
            dot += x * y;
            na += x * x;
            nb += y * y;
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}