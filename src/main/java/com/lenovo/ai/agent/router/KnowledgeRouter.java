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

    /**
     * 路由相似度阈值。
     *
     * <p><b>修复</b>：原为硬编码 0.5 且低于阈值直接返回 null。
     * 短问题（如"审核人是谁"）与知识库描述的余弦相似度往往达不到 0.5，
     * 于是路由失败 → 工具报"无法判断知识库" → 模型转而反问用户，
     * 明明知识库里就有答案。现改为可配置，且低于阈值也返回最相近的库。</p>
     */
    @Value("${ai.knowledge-router.min-score:0.3}")
    private double minScore;

    private static final int MAX_DESC_LENGTH = 500;

    /** 并行刷新的并发度，避免打爆下游 embedding 服务 */
    private static final int REFRESH_CONCURRENCY = 4;

    private final KnowledgeService knowledgeService;
    private final EmbeddingModel embeddingModel;
    /** 问题 embedding 的超时（秒），超时后降级到兜底知识库 */
    private static final int EMBED_TIMEOUT_SECONDS = 2;
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

    /**
     * 兜底知识库 ID：向量不可用时直接使用。
     *
     * <p><b>强烈建议配置</b>：当知识库数量少（尤其只有 1 个）、
     * 或 embedding 服务不稳定时，路由意义不大且极易失败。
     * 配上它等于给知识库检索上了保险。</p>
     */
    @Value("${ai.knowledge-router.default-kb-id:}")
    private String defaultKbId;

    /**
     * 路由到知识库。
     *
     * <p>兜底顺序：<b>单库直返 → 缓存空用默认库 → 相似度选最优（低于阈值也返回最优）</b>。
     * 只在"既没缓存、又没配默认库"时才返回 null。</p>
     */
    public Long route(String question) {
        if (question == null || question.isBlank()) return null;
        ensureReady();

        if (kbVectors.isEmpty()) {
            Long fallback = parseId(defaultKbId);
            if (fallback != null) {
                log.warn(">>> 知识库向量缓存为空（可能是 projectId 配错或 embedding 不可用），"
                        + "使用兜底知识库 {} —— 建议检查 KnowledgeRouter.diagnose()", fallback);
                return fallback;
            }
            log.warn(">>> 知识库向量缓存为空且未配置 ai.knowledge-router.default-kb-id，路由失败");
            return null;
        }

        if (kbVectors.size() == 1) {
            Long only = kbVectors.keySet().iterator().next();
            log.info(">>> 仅一个知识库 {}，跳过相似度路由", only);
            return only;
        }

        // ★ R6 修复：给 embedding 加超时。
        //   embedding 服务抖动时，route 会同步阻塞，拖垮整轮首字延迟。
        //   超时后直接返回兜底知识库（若有），或最相近的库。
        float[] questionVec;
        try {
            questionVec = CompletableFuture.supplyAsync(
                            () -> embeddingModel.embed(question).content().vector(),
                            refreshPool)
                    .orTimeout(EMBED_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .join();
        } catch (Exception e) {
            log.warn(">>> 问题 embedding 超时或失败({}s)，降级到兜底知识库",
                    EMBED_TIMEOUT_SECONDS, e);
            Long fallback = parseId(defaultKbId);
            if (fallback != null) return fallback;
            // 没配兜底库：返回第一个缓存的知识库（宁可贵一点也不能不查）
            Long any = kbVectors.keySet().iterator().next();
            log.warn(">>> 未配置 default-kb-id，返回任意知识库 {}", any);
            return any;
        }

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
        if (bestScore < minScore) {
            log.warn(">>> 路由相似度 {} 低于阈值 {}，仍返回最相近知识库 {}",
                    bestScore, minScore, bestKbId);
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
            if (ids == null || ids.isEmpty()) {
                // ★ 升为 WARN：这是"知识库永远查不到"的头号原因。
                //   projectId / itCode 配错时，列表为空 → kbVectors 恒空
                //   → route() 恒返回 null → 工具报"无法判断知识库" → 模型反问用户。
                log.warn(">>> 查询知识库列表为空！请检查配置 ai.knowledge-router.project-id={}, "
                                + "ai.knowledge-router.it-code={} 是否正确（projectId 默认 79 是硬编码遗留值）",
                        projectId, itCode);
                return new ArrayList<>();
            }
            log.info(">>> 查询到 {} 个知识库: {}", ids.size(), ids);
            return ids;
        } catch (Exception e) {
            log.error(">>> 查询知识库列表失败! projectId={}, itCode={}", projectId, itCode, e);
            return new ArrayList<>();
        }
    }

    /**
     * 输出当前状态，便于排查"知识库查不到"类问题。
     *
     * <p>建议在项目启动后手动调用一次，或在定时任务里定期打印。
     * 典型用法：在任意 Controller / Runner 注入 KnowledgeRouter 后调用本方法打日志。</p>
     *
     * @return 人类可读的状态摘要
     */
    public String diagnose() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== KnowledgeRouter 诊断 ==========\n");
        sb.append("projectId=").append(projectId)
                .append("  (配置: ai.knowledge-router.project-id)\n");
        sb.append("itCode=").append(itCode)
                .append("  (配置: ai.knowledge-router.it-code)\n");
        sb.append("minScore=").append(minScore)
                .append("  (配置: ai.knowledge-router.min-score)\n");
        sb.append("缓存知识库数量=").append(kbVectors.size()).append("\n");

        if (kbVectors.isEmpty()) {
            sb.append("❌ 向量缓存为空 —— 路由将永远失败！\n");
            sb.append("   可能原因：\n");
            sb.append("   1. projectId 配置错误（查不到知识库列表）\n");
            sb.append("   2. embedding 服务不可用\n");
            sb.append("   3. 后台刷新尚未完成（等待下一轮，间隔 ")
                    .append(refreshMinutes).append(" 分钟）\n");
        } else {
            sb.append("✅ 向量缓存正常，已加载知识库: ").append(kbVectors.keySet()).append("\n");
        }
        sb.append("==========================================\n");
        return sb.toString();
    }

    /** 解析兜底知识库 ID（空/非法返回 null） */
    private Long parseId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            log.warn(">>> ai.knowledge-router.default-kb-id 配置非法: {}（需为纯数字）", s);
            return null;
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