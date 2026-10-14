package com.lenovo.controller.ai;

import com.lenovo.ai.memory.store.MemoryMaintenanceStore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-10-14 14:34
 * @project iam-backend-ai
 * @description
 */
@RestController
@RequestMapping("/ai/admin/memory")
@RequiredArgsConstructor
public class MemoryAdminController {

    private final MemoryMaintenanceStore memoryMaintenanceStore;

    @PostMapping("/decay/{userId}")
    public Map<String, Object> decay(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0.95") double factor) {
        int count = memoryMaintenanceStore.decayAll(userId, factor);
        return Map.of(
                "userId", userId,
                "factor", factor,
                "decayedCount", count    // ★ 现在可以返回真实条数
        );
    }

    @PostMapping("/clean/{userId}")
    public Map<String, Object> clean(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0.1") double threshold) {
        int count = memoryMaintenanceStore.deleteBelow(userId, threshold);
        return Map.of(
                "userId", userId,
                "threshold", threshold,
                "deletedCount", count    // ★ 现在可以返回真实条数
        );
    }
}