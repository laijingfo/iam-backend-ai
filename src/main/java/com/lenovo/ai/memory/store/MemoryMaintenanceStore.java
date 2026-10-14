package com.lenovo.ai.memory.store;

import com.lenovo.ai.memory.model.MemoryItem;

import java.util.List;
import java.util.Set;

/**
 * 记忆维护类操作：失效、衰减、清理、活跃用户查询
 *
 * @author laifo
 * @date 2026-10-13
 */
public interface MemoryMaintenanceStore {

    /** 置记忆为无效 */
    void invalidate(String memoryId);

    /** 全量衰减某用户的所有记忆 */
    void decayAll(String userId, double factor);

    /** 删除重要性低于阈值的记忆 */
    void deleteBelow(String userId, double threshold);

    /** 获取所有活跃用户（有记忆记录的用户） */
    Set<String> getAllActiveUsers();

    /** 查用户的所有记忆（调试用） */
    List<MemoryItem> findAllByUser(String userId);
}