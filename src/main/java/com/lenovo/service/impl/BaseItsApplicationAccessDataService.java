package com.lenovo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.entity.ItsApplicationAccessData;
import com.lenovo.mapper.ItsApplicationAccessDataServiceMapper;
import com.lenovo.service.ItsApplicationAccessDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-08-31 09:41
 * @project iam-backend
 * @description
 */
@Slf4j
public abstract class BaseItsApplicationAccessDataService
        extends ServiceImpl<ItsApplicationAccessDataServiceMapper, ItsApplicationAccessData>
        implements ItsApplicationAccessDataService {

    private static final int BATCH_SIZE = 500;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);

    @Transactional(rollbackFor = Exception.class)
    public void insertParallel(List<ItsApplicationAccessData> total) throws Exception {
        // 1. 分片
        List<List<ItsApplicationAccessData>> parts = new ArrayList<>();
        for (int start = 0; start < total.size(); start += BATCH_SIZE) {
            parts.add(total.subList(start, Math.min(start + BATCH_SIZE, total.size())));
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (List<ItsApplicationAccessData> part : parts) {
            CompletableFuture<Void> f =
                    CompletableFuture.runAsync(() -> batchInsert(part), POOL);
            futures.add(f);
        }

        // 2. 等待全部完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    public void batchInsert(List<ItsApplicationAccessData> list) {
        getBaseMapper().batchInsert(list);
    }
}
