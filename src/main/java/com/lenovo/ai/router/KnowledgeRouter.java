package com.lenovo.ai.router;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lenovo.ai.KnowledgeService;
import com.lenovo.bean.knowledge.ProjectBase;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-24 17:14
 * @project iam-backend-ai
 * @description
 * 知识库向量路由：根据用户问题，自动选择最相关的知识库。
 *
 * <p>向量缓存采用"后台预热 + 失败自愈"：启动阶段不再同步阻塞调用远端 embedding 接口。
 * 否则一次网络抖动（例如双栈域名走到没有出口的 IPv6，TLS 握手被 RST）就会拖慢启动，
 * 并让 kbVectors 永久为空，导致 route()/routeTopN() 此后恒定返回空结果且无法恢复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeRouter {

    private static final Integer DEFAULT_PROJECT_ID = 79;
    private static final String DEFAULT_IT_CODE = "laijf2";
    private static final double MIN_SCORE = 0.5;
    /** 知识库描述送入 embedding 前的最大长度。 */
    private static final int MAX_DESC_LENGTH = 500;

    private final KnowledgeService knowledgeService;
    private final EmbeddingModel embeddingModel;

    /** 向量缓存刷新间隔（分钟），同时也是失败后的自愈重试间隔。 */
    @Value("${ai.knowledge-router.refresh-minutes:10}")
    private long refreshMinutes;

    /** 请求链路上做兜底重建的最小间隔（毫秒），避免每个请求都去打下游。 */
    @Value("${ai.knowledge-router.retry-backoff-millis:60000}")
    private long retryBackoffMillis;

    private final Map<Long, float[]> kbVectors = new ConcurrentHashMap<>();
    private final Map<Long, String> kbDescriptions = new ConcurrentHashMap<>();

    /** 刷新互斥标志：保证同一时刻只有一个线程在重建向量。 */
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private volatile long lastAttemptAt = 0L;

    private final ScheduledExecutorService refresher = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "knowledge-router-refresher");
        thread.setDaemon(true);
        return thread;
    });

    @PostConstruct
    public void init() {
        long interval = Math.max(1L, refreshMinutes);
        // 不在启动线程里做远程调用：预热失败也不影响应用启动，后续定时任务会继续自愈
        refresher.scheduleWithFixedDelay(this::refreshQuietly, 0L, interval, TimeUnit.MINUTES);
        log.info(">>> KnowledgeRouter 已启动后台向量刷新，间隔 {} 分钟", interval);
    }

    @PreDestroy
    public void destroy() {
        refresher.shutdownNow();
    }

    private void refreshQuietly() {
        try {
            refresh();
        } catch (Exception e) {
            // 刷新失败时保留已有缓存继续服务，下一轮再试
            log.error(">>> KnowledgeRouter 向量刷新失败，沿用现有缓存（{} 个）", kbVectors.size(), e);
        }
    }

    /**
     * 重建知识库描述向量。单个知识库失败不影响其他知识库，失败项留待下一轮重试。
     *
     * @return true 表示本次确实执行了刷新；false 表示已有线程在刷新，本次直接跳过
     */
    private boolean refresh() {
        if (!refreshing.compareAndSet(false, true)) {
            return false;
        }
        lastAttemptAt = System.currentTimeMillis();
        try {
            List<Long> kbIds = getAllKnowledgeBaseIds();
            int success = 0;
            for (Long kbId : kbIds) {
                try {
                    String desc = buildDescription(kbId);
                    if (desc == null || desc.isBlank()) continue;
                    if (desc.length() > MAX_DESC_LENGTH) desc = desc.substring(0, MAX_DESC_LENGTH);
                    float[] vec = embeddingModel.embed(desc).content().vector();
                    kbVectors.put(kbId, vec);
                    kbDescriptions.put(kbId, desc);
                    success++;
                    log.debug(">>> 知识库 {} 描述向量已生成，维度：{}", kbId, vec.length);
                } catch (Exception e) {
                    log.warn(">>> 知识库 {} 描述向量生成失败，将在下次刷新时重试", kbId, e);
                }
            }
            log.info(">>> KnowledgeRouter 刷新完成，本次成功 {} / 共 {} 个知识库，当前缓存 {} 个",
                    success, kbIds.size(), kbVectors.size());
            return true;
        } finally {
            refreshing.set(false);
        }
    }

    /**
     * 请求链路上的兜底：缓存为空时（后台预热还没跑完，或一直失败）尝试就地重建。
     * 带节流与互斥，避免把下游打爆或让并发请求重复重建。
     */
    private void ensureReady() {
        if (!kbVectors.isEmpty()) return;
        if (System.currentTimeMillis() - lastAttemptAt < retryBackoffMillis) return;
        try {
            refresh();
        } catch (Exception e) {
            log.warn(">>> KnowledgeRouter 兜底重建向量失败", e);
        }
    }

    public Long route(String question) {
        if (question == null || question.isBlank()) return null;
        ensureReady();
        if (kbVectors.isEmpty()) {
            log.warn(">>> 知识库向量缓存为空，跳过路由，将由调用方走默认知识库");
            return null;
        }
        float[] questionVec = embeddingModel.embed(question).content().vector();

        Long bestKbId = null;
        double bestScore = -1;
        for (Map.Entry<Long, float[]> entry : kbVectors.entrySet()) {
            double score = cosine(questionVec, entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                bestKbId = entry.getKey();
            }
        }

        log.info(">>> 路由结果：knowledgeBaseId={}, score={}", bestKbId, bestScore);
        if (bestScore < MIN_SCORE) {
            log.warn(">>> 路由相似度低于阈值 {}，不选知识库", MIN_SCORE);
            return null;
        }
        return bestKbId;
    }

    public List<Long> routeTopN(String question, int n) {
        if (question == null || question.isBlank()) return List.of();
        ensureReady();
        if (kbVectors.isEmpty()) return List.of();
        float[] questionVec = embeddingModel.embed(question).content().vector();

        List<Map.Entry<Long, float[]>> entries = new ArrayList<>(kbVectors.entrySet());
        entries.sort(Comparator.comparingDouble(
                (Map.Entry<Long, float[]> e) -> -cosine(questionVec, e.getValue())));

        List<Long> result = new ArrayList<>();
        for (int i = 0; i < Math.min(n, entries.size()); i++) {
            result.add(entries.get(i).getKey());
        }
        return result;
    }

    private String buildDescription(Long kbId) {
        StringBuilder sb = new StringBuilder();

        try {
            JSONObject docResp = knowledgeService.getDocuments(String.valueOf(kbId));
            JSONArray docs = docResp.getJSONArray("result");
            if (docs == null) docs = docResp.getJSONArray("data");
            if (docs != null && !docs.isEmpty()) {
                sb.append("知识库 ").append(kbId).append(" 包含文档：");
                int count = 0;
                for (int i = 0; i < docs.size() && count < 10; i++) {
                    JSONObject o = docs.getJSONObject(i);
                    JSONArray rows = o.getJSONArray("rows");
                    if (rows == null) continue;
                    for (int j = 0; j < rows.size() && count < 10; j++) {
                        String title = rows.getJSONObject(j).getString("originName");
                        if (title != null && !title.isBlank()) {
                            sb.append(title).append("；");
                            count++;
                        }
                    }
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            log.warn("获取知识库 {} 文档失败", kbId, e);
        }

        return sb.toString();
    }

    private List<Long> getAllKnowledgeBaseIds() {
        try {
            ProjectBase base = new ProjectBase();
            base.setProjectId(DEFAULT_PROJECT_ID);
            base.setItCode(DEFAULT_IT_CODE);
            List<Long> ids = knowledgeService.queryKnowledgeBase(base);
            return ids == null ? new ArrayList<>() : ids;
        } catch (Exception e) {
            log.warn("查询知识库列表失败", e);
            return new ArrayList<>();
        }
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
