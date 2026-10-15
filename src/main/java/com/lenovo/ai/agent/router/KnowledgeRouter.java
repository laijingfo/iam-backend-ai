package com.lenovo.ai.agent.router;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lenovo.ai.service.KnowledgeService;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 知识库向量路由：根据用户问题，自动选择最相关的知识库。
 *
 * <p>向量缓存采用"后台预热 + 失败自愈"：启动阶段不再同步阻塞调用远端 embedding 接口。
 * 否则一次网络抖动就会拖慢启动，并让 kbVectors 永久为空。</p>
 *
 * <p>刷新时并行处理各知识库（默认并发 4），单库失败不影响其他。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeRouter {

    private static final double MIN_SCORE = 0.5;
    private static final int MAX_DESC_LENGTH = 500;

    /** 并行刷新的并发度，避免打爆下游 embedding 服务 */
    private static final int REFRESH_CONCURRENCY = 4;

    private final KnowledgeService knowledgeService;
    private final EmbeddingModel embeddingModel;

    /**
     * 知识库所属项目 ID。
     * <p><b>修复</b>：原为硬编码 {@code 79}，不同环境（dev / test / prod）
     * 项目 ID 不同，无法切换。改为配置项。</p>
     */
    @Value("${ai.knowledge-router.project-id:79}")
    private Integer projectId;

    /**
     * 查询知识库列表时使用的 itCode。
     * <p><b>修复</b>：原为硬编码 {@code laijf2}（某位开发同学的账号），
     * 属个人信息泄漏且不可配置。改为配置项。</p>
     */
    @Value("${ai.knowledge-router.it-code:laijf2}")
    private String itCode;

    @Value("${ai.knowledge-router.refresh-minutes:10}")
    private long refreshMinutes;

    @Value("${ai.knowledge-router.retry-backoff-millis:60000}")
    private long retryBackoffMillis;

    private final Map<Long, float[]> kbVectors = new ConcurrentHashMap<>();
    private final Map<Long, String> kbDescriptions = new ConcurrentHashMap<>();

    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private volatile long lastAttemptAt = 0L;

    /** 后台定时刷新调度器（单线程，只负责触发） */
    private final ScheduledExecutorService refresher =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "knowledge-router-refresher");
                t.setDaemon(true);
                return t;
            });

    /** ★ 刷新用线程池（并行处理多个知识库） */
    private final ExecutorService refreshPool =
            Executors.newFixedThreadPool(REFRESH_CONCURRENCY, r -> {
                Thread t = new Thread(r, "kb-refresh-");
                t.setDaemon(true);
                return t;
            });

    @PostConstruct
    public void init() {
        long interval = Math.max(1L, refreshMinutes);
        refresher.scheduleWithFixedDelay(this::refreshQuietly, 0L, interval, TimeUnit.MINUTES);
        log.info(">>> KnowledgeRouter 已启动后台向量刷新，间隔 {} 分钟，并发度 {}",
                interval, REFRESH_CONCURRENCY);
    }

    @PreDestroy
    public void destroy() {
        refresher.shutdownNow();
        refreshPool.shutdownNow();
    }

    private void refreshQuietly() {
        try {
            refresh();
        } catch (Exception e) {
            log.error(">>> KnowledgeRouter 向量刷新失败，沿用现有缓存（{} 个）",
                    kbVectors.size(), e);
        }
    }

    /**
     * 重建知识库描述向量。
     * <p>并行处理各知识库，单个失败不影响其他，失败项留待下一轮重试。</p>
     *
     * @return true 表示本次确实执行了刷新；false 表示已有线程在刷新
     */
    private boolean refresh() {
        if (!refreshing.compareAndSet(false, true)) {
            return false;
        }
        lastAttemptAt = System.currentTimeMillis();
        try {
            List<Long> kbIds = getAllKnowledgeBaseIds();
            if (kbIds.isEmpty()) {
                log.info(">>> KnowledgeRouter 刷新完成，无知识库");
                return true;
            }

            // ★ 并行构建，单个失败不影响其他
            List<CompletableFuture<Boolean>> futures = kbIds.stream()
                    .map(kbId -> CompletableFuture.supplyAsync(
                            () -> buildOne(kbId), refreshPool))
                    .toList();

            // 等待全部完成（不抛异常，单个失败已在 buildOne 里吞掉）
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            long success = futures.stream()
                    .map(f -> {
                        try { return f.getNow(false); }
                        catch (Exception e) { return false; }
                    })
                    .filter(Boolean::booleanValue)
                    .count();

            log.info(">>> KnowledgeRouter 刷新完成，本次成功 {} / 共 {} 个知识库，当前缓存 {} 个",
                    success, kbIds.size(), kbVectors.size());
            return true;
        } finally {
            refreshing.set(false);
        }
    }

    /** 构建单个知识库的向量，成功返回 true，失败返回 false */
    private boolean buildOne(Long kbId) {
        try {
            String desc = buildDescription(kbId);
            if (desc == null || desc.isBlank()) return false;
            if (desc.length() > MAX_DESC_LENGTH) desc = desc.substring(0, MAX_DESC_LENGTH);
            float[] vec = embeddingModel.embed(desc).content().vector();
            kbVectors.put(kbId, vec);
            kbDescriptions.put(kbId, desc);
            log.debug(">>> 知识库 {} 描述向量已生成，维度：{}", kbId, vec.length);
            return true;
        } catch (Exception e) {
            log.warn(">>> 知识库 {} 描述向量生成失败，将在下次刷新时重试", kbId, e);
            return false;
        }
    }

    /**
     * 请求链路上的兜底：缓存为空时（后台预热还没跑完，或一直失败）触发一次重建。
     *
     * <p><b>修复</b>：原实现会同步调用 {@code refresh()}，即首个请求会
     * 阻塞等待全部知识库的 embedding 完成（冷启动或下游抖动时可达数秒甚至更久），
     * 直接拖垮首字延迟。改为<b>异步提交</b>：本次请求不等待，
     * 缓存仍为空则返回 null，由调用方走默认知识库。</p>
     *
     * <p>仍保留节流与互斥，避免把下游打爆或让并发请求重复重建。</p>
     */
    private void ensureReady() {
        if (!kbVectors.isEmpty()) return;
        if (System.currentTimeMillis() - lastAttemptAt < retryBackoffMillis) return;

        try {
            refreshPool.submit(this::refreshQuietly);
            log.info(">>> KnowledgeRouter 缓存为空，已提交异步重建，本次请求走默认知识库");
        } catch (Exception e) {
            log.warn(">>> KnowledgeRouter 提交异步重建失败", e);
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
            base.setProjectId(projectId);
            base.setItCode(itCode);
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