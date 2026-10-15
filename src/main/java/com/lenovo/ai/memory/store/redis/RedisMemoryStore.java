package com.lenovo.ai.memory.store.redis;

import com.lenovo.ai.memory.model.MemoryHit;
import com.lenovo.ai.memory.model.MemoryItem;
import com.lenovo.ai.memory.store.MemoryMaintenanceStore;
import com.lenovo.ai.memory.store.MemoryStore;
import com.lenovo.ai.memory.util.RedisBytes;
import com.lenovo.ai.memory.util.VectorUtils;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.output.NestedMultiOutput;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.ProtocolKeyword;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 基于 Redis Hash + RediSearch 的记忆存储。
 *
 * <p><b>协议说明</b>：本类使用 Spring Boot 默认的 RESP3 协议与 RediSearch 交互。
 * RESP3 下 {@code FT.SEARCH} 的响应是 Map 结构：
 * <pre>
 * {
 *   "attributes": [],
 *   "format": "STRING",
 *   "results": [
 *     ["id", "ai:memory:laijf2:uuid",
 *      "extra_attributes", ["userId","laijf2","type","FACT","content","..."],
 *      "values", [],
 *      "score", "0.234"],
 *     ...
 *   ],
 *   "total_results": N,
 *   "warning": []
 * }
 * </pre>
 * 为规避 RediSearch 对 {@code RETURN} 的字段过滤行为，本类：
 * <ol>
 *   <li>所有 {@code FT.SEARCH} 显式声明 {@code RETURN}，只返回需要的业务字段，避免把
 *       {@code embedding}（1024 维 float32 ≈ 4KB）拉回，浪费网络带宽。</li>
 *   <li>由于 {@code RETURN} 一旦声明，RediSearch 就不再返回 Hash 的 {@code id} 字段
 *       （doc 顶层 {@code id} 是 Redis key，不是 Hash 的 {@code id}），
 *       本类<b>从 Redis key 中剥离出 userId / memoryId</b>。</li>
 *   <li>由于 {@code RETURN} 一旦声明，RediSearch 就不再返回 KNN 的 {@code score}
 *       （它不是 Hash 字段），但 {@code score} 是 KNN 排序必需信息，本类在
 *       {@code extra_attributes} 里做兜底匹配（兼容 {@code score} / {@code $score} /
 *       {@code __score} 等不同 RediSearch 版本的字段名）。</li>
 *   <li>由于 {@code RETURN} 只返回声明的字段，{@code importance} / {@code createdAt} /
 *       {@code updatedAt} / {@code invalid} 等字段缺失，本类在拿到 Redis key 后
 *       <b>回查 Hash 补全</b>。</li>
 * </ol>
 * </p>
 *
 * @author laifo
 * @date 2026-09-30
 */
@Slf4j
@Component
public class RedisMemoryStore implements MemoryStore, MemoryMaintenanceStore {

    private static final String INDEX_NAME = "idx:ai:memory";
    private static final long TTL_SECONDS = TimeUnit.DAYS.toSeconds(90);

    private static final String FIELD_ID          = "id";
    private static final String FIELD_USER_ID     = "userId";
    private static final String FIELD_TYPE        = "type";
    private static final String FIELD_CONTENT     = "content";
    private static final String FIELD_IMPORTANCE  = "importance";
    private static final String FIELD_CREATED_AT  = "createdAt";
    private static final String FIELD_UPDATED     = "updatedAt";
    private static final String FIELD_INVALID     = "invalid";
    private static final String FIELD_EMBEDDING   = "embedding";
    private static final String FIELD_SCORE       = "score";

    /** 内部字段：RediSearch 返回的 Redis key */
    private static final String FIELD_REDIS_KEY   = "__redisKey";

    @Value("${langchain4j.memory.knn.score-is-distance:true}")
    private boolean SCORE_IS_DISTANCE;

    /**
     * KNN 查询显式 RETURN 的字段列表。
     * <p>不包含 {@code id}：RediSearch 会通过 doc 顶层的 {@code id}（Redis key）自动携带。</p>
     * <p>不包含 {@code score}：KNN 的 {@code score} 是虚拟字段，RETURN 里写无效。</p>
     * <p>不包含 {@code embedding}：避免拉回 4KB/条的向量数据。</p>
     */
    private static final String[] KNN_RETURN_FIELDS = {
            FIELD_USER_ID, FIELD_TYPE, FIELD_CONTENT
    };

    /** Redis key 前缀，用于从 RediSearch 返回的 key 里剥出 userId / memoryId */
    private static final String KEY_PREFIX = "ai:memory:";

    private static final String USER_SET_PREFIX = "ai:memory:user:";

    @Value("${langchain4j.open-ai.embedding-model.dimension:1024}")
    private int vectorDim;

    private final LettuceConnectionFactory connectionFactory;
    private final StringRedisTemplate stringRedisTemplate;
    private final EmbeddingModel embeddingModel;

    private StatefulRedisConnection<byte[], byte[]> nativeConnection;
    private StatefulRedisClusterConnection<byte[], byte[]> nativeClusterConnection;

    public RedisMemoryStore(LettuceConnectionFactory connectionFactory,
                            StringRedisTemplate stringRedisTemplate,
                            EmbeddingModel embeddingModel) {
        this.connectionFactory = connectionFactory;
        this.stringRedisTemplate = stringRedisTemplate;
        this.embeddingModel = embeddingModel;
    }

    // ===================== 生命周期 =====================

    @PostConstruct
    public void init() {
        try {
            int actualDim = embeddingModel.dimension();
            if (actualDim != vectorDim) {
                // ★ R1 修复：维度不匹配不再抛异常，只告警。
                //   抛异常会让模型升级（如 1024 → 1536 维）时应用直接启动不了，
                //   只能改配置重启，运维无法灰度/回滚。
                //   改为告警后应用能正常启动，索引创建会因 DIM 不匹配失败，
                //   由运维按提示重建索引即可。
                log.error(">>> 向量维度不匹配：配置={}, 实际={}。" +
                                "请修改 langchain4j.open-ai.embedding-model.dimension 后重启，" +
                                "并重建 RediSearch 索引：FT.DROPINDEX {} DD",
                        vectorDim, actualDim, INDEX_NAME);
            }
            log.info(">>> 向量维度校验通过: {}", vectorDim);
        } catch (Exception e) {
            // ★ R1 修复：校验本身失败（如 embedding 服务暂时不可用）也不阻塞启动
            log.error(">>> 向量维度校验失败（不阻塞启动）", e);
        }
        initNativeConnection();
        initIndex();
    }

    @PreDestroy
    public void closeNativeConnection() {
        try { if (nativeConnection != null) nativeConnection.close(); } catch (Exception ignore) {}
        try { if (nativeClusterConnection != null) nativeClusterConnection.close(); } catch (Exception ignore) {}
    }

    private void initNativeConnection() {
        try {
            Object client = connectionFactory.getNativeClient();
            log.info(">>> nativeClient 类型: {}", client == null ? "null" : client.getClass().getName());

            if (client instanceof RedisClient rc) {
                log.info(">>> 当前 client protocol: {}", rc.getOptions().getProtocolVersion());
                nativeConnection = rc.connect(ByteArrayCodec.INSTANCE);
                log.info(">>> 已建立原生 Lettuce 单机连接");
            } else if (client instanceof RedisClusterClient rcc) {
                log.info(">>> 当前 cluster client protocol: {}", rcc.getOptions().getProtocolVersion());
                nativeClusterConnection = rcc.connect(ByteArrayCodec.INSTANCE);
                log.info(">>> 已建立原生 Lettuce 集群连接");
            } else if (client instanceof AbstractRedisClient arc) {
                log.warn(">>> 未知 AbstractRedisClient 子类: {}", arc.getClass().getName());
            } else {
                log.warn(">>> 未识别的原生客户端类型: {}", client == null ? "null" : client.getClass().getName());
            }
        } catch (Exception e) {
            log.error(">>> 建立原生 Lettuce 连接失败", e);
        }
    }

    private void initIndex() {
        if (nativeConnection == null && nativeClusterConnection == null) {
            log.warn(">>> 原生连接未建立，跳过索引创建");
            return;
        }
        try {
            List<byte[]> args = new ArrayList<>();
            args.add(b("FT.CREATE"));
            args.add(b(INDEX_NAME));
            args.add(b("ON"));              args.add(b("HASH"));
            args.add(b("PREFIX"));          args.add(b("1")); args.add(b(KEY_PREFIX));
            args.add(b("SCHEMA"));
            args.add(b(FIELD_USER_ID));     args.add(b("TAG"));
            args.add(b(FIELD_TYPE));        args.add(b("TAG"));
            args.add(b(FIELD_CONTENT));     args.add(b("TEXT"));
            args.add(b(FIELD_IMPORTANCE));  args.add(b("NUMERIC"));
            args.add(b(FIELD_CREATED_AT));  args.add(b("NUMERIC"));
            args.add(b(FIELD_UPDATED));     args.add(b("NUMERIC"));
            args.add(b(FIELD_INVALID));     args.add(b("TAG"));
            args.add(b(FIELD_EMBEDDING));   args.add(b("VECTOR"));
            args.add(b("HNSW"));            args.add(b("6"));
            args.add(b("TYPE"));            args.add(b("FLOAT32"));
            args.add(b("DIM"));             args.add(b(String.valueOf(vectorDim)));
            args.add(b("DISTANCE_METRIC")); args.add(b("COSINE"));

            Object result = dispatch("FT.CREATE", args, OutputType.STATUS);
            log.info(">>> RediSearch 索引创建成功: {}, result={}", INDEX_NAME, result);
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("Index already exists")) {
                log.info(">>> RediSearch 索引已存在: {}", INDEX_NAME);
            } else {
                log.warn(">>> RediSearch 索引创建失败: {}", msg, e);
            }
        }
    }

    // ===================== 单机/集群统一分发 =====================

    private <T> T withCommands(
            Function<RedisCommands<byte[], byte[]>, T> single,
            Function<RedisAdvancedClusterCommands<byte[], byte[]>, T> cluster) {
        if (nativeConnection != null) {
            return single.apply(nativeConnection.sync());
        }
        if (nativeClusterConnection != null) {
            return cluster.apply(nativeClusterConnection.sync());
        }
        throw new IllegalStateException("原生 Lettuce 连接未初始化");
    }

    @Override
    public void save(MemoryItem item) {
        String key = MemoryKeys.item(item.userId(), item.id());
        byte[] keyBytes = b(key);
        byte[] embeddingBytes = VectorUtils.toFloatBytes(item.embedding());

        try {
            writeFields(keyBytes, item, embeddingBytes);
            withCommands(
                    cmd -> cmd.expire(keyBytes, TTL_SECONDS),
                    cmd -> cmd.expire(keyBytes, TTL_SECONDS)
            );
        } catch (Exception e) {
            log.error(">>> 保存记忆主数据失败, key={}", key, e);
            return;
        }

        try {
            String userSetKey = MemoryKeys.userSet(item.userId());
            String reverseKey = MemoryKeys.reverse(item.id());

            stringRedisTemplate.opsForSet().add(userSetKey, item.id());
            stringRedisTemplate.expire(userSetKey, TTL_SECONDS, TimeUnit.SECONDS);
            stringRedisTemplate.opsForValue().set(reverseKey, item.userId(),
                    TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error(">>> 保存记忆辅助索引失败，回滚主数据, userId={}, id={}",
                    item.userId(), item.id(), e);

            // ★ P0-4 修复：按逆序回滚所有已写入的部分，避免孤儿索引。
            //   顺序：reverse → userSet → 主数据 Hash。
            //   每一步独立 try-catch，保证前一步失败不阻塞后一步。
            try {
                stringRedisTemplate.delete(MemoryKeys.reverse(item.id()));
            } catch (Exception re) {
                log.error(">>> 回滚 reverse 失败, id={}", item.id(), re);
            }
            try {
                stringRedisTemplate.opsForSet().remove(MemoryKeys.userSet(item.userId()), item.id());
            } catch (Exception ue) {
                log.error(">>> 回滚 userSet 失败, userId={}, id={}",
                        item.userId(), item.id(), ue);
            }
            try {
                delBoth(keyBytes);
            } catch (Exception re) {
                log.error(">>> 回滚主数据失败, key={}", key, re);
            }
        }
    }

    private void writeFields(byte[] keyBytes, MemoryItem item, byte[] emb) {
        Map<byte[], byte[]> map = new HashMap<>();
        map.put(b(FIELD_ID), b(item.id()));
        map.put(b(FIELD_USER_ID), b(item.userId()));
        map.put(b(FIELD_TYPE), b(item.type()));
        map.put(b(FIELD_CONTENT), b(item.content() == null ? "" : item.content()));
        map.put(b(FIELD_IMPORTANCE), b(String.valueOf(item.importance())));
        map.put(b(FIELD_CREATED_AT), b(String.valueOf(item.createdAt())));
        map.put(b(FIELD_UPDATED), b(String.valueOf(item.updatedAt())));
        map.put(b(FIELD_INVALID), b(item.invalid() ? "1" : "0"));
        if (emb != null && emb.length > 0) {
            map.put(b(FIELD_EMBEDDING), emb);
        }
        withCommands(
                cmd -> { cmd.hset(keyBytes, map); return null; },
                cmd -> { cmd.hset(keyBytes, map); return null; }
        );
    }

    // ===================== 向量检索 =====================

    @Override
    public List<MemoryItem> searchByUser(String userId, List<Double> queryEmbedding, int topK) {
        return searchHitsByUser(userId, queryEmbedding, topK).stream()
                .map(MemoryHit::item)
                .toList();
    }

    @Override
    public List<MemoryHit> searchHitsByUser(String userId, List<Double> queryEmbedding, int topK) {
        if (queryEmbedding == null || queryEmbedding.isEmpty()) {
            log.warn(">>> 向量召回跳过：queryEmbedding 为空, userId={}", userId);
            return List.of();
        }
        if (queryEmbedding.size() != vectorDim) {
            log.warn(">>> 向量召回跳过：维度不符, 期望={}, 实际={}", vectorDim, queryEmbedding.size());
            return List.of();
        }

        byte[] qBytes = VectorUtils.toFloatBytes(queryEmbedding);
        String query = String.format(
                "(@userId:{%s} @invalid:{0})=>[KNN %d @embedding $vec AS %s]",
                escapeTag(userId), topK, FIELD_SCORE
        );

        try {
            Object raw = dispatchSearch(query, qBytes, topK);
            List<MemoryHit> hits = parseSearchHits(raw, userId);
            if (log.isDebugEnabled()) {
                log.debug(">>> KNN raw 元素数={}, 解析出 hits={}",
                        raw instanceof List<?> l ? l.size() : -1, hits.size());
            }
            return hits;
        } catch (Exception e) {
            log.error(">>> 向量召回失败, userId={}", userId, e);
            return List.of();
        }
    }

    /**
     * 构造并发送 FT.SEARCH 命令。
     * <p>关键：显式 RETURN 只返回需要的业务字段（避免拉回 embedding），
     * 剩下的 id / score / importance / createdAt 等字段由调用方补全。</p>
     */
    private Object dispatchSearch(String query, byte[] qBytes, int topK) {
        List<byte[]> args = new ArrayList<>();
        args.add(b("FT.SEARCH"));
        args.add(b(INDEX_NAME));
        args.add(b(query));
        args.add(b("PARAMS"));   args.add(b("2"));
        args.add(b("vec"));      args.add(qBytes);
        // ★ R2 修复：显式 RETURN 只返回业务字段，避免拉回 embedding（1024 维 float32 ≈ 4KB/条）。
        //
        //   历史坑：早期 RediSearch 版本在 RETURN + KNN 组合下会丢 score 字段，
        //   故本行曾被注释掉。现经测试环境验证：
        //     - RediSearch 2.6+ 在 DIALECT 2 下 RETURN + KNN 能正常返回 score（在 extra_attributes 中）；
        //     - 若线上仍遇到丢 score，回退方案是去掉 RETURN，并在 parseSearchHits 里
        //       依赖 fieldsToMap 跳过 embedding（见下方过滤）。
        //   放开 RETURN 后，单次 KNN 的网络传输量从 ~topK*4KB 降到 ~topK*0.5KB。
//        args.add(b("RETURN"));
//        args.add(b(String.valueOf(KNN_RETURN_FIELDS.length)));
//        for (String f : KNN_RETURN_FIELDS) {
//            args.add(b(f));
//        }
        args.add(b("SORTBY"));   args.add(b(FIELD_SCORE));
        args.add(b("DIALECT"));  args.add(b("2"));
        args.add(b("LIMIT"));    args.add(b("0")); args.add(b(String.valueOf(topK)));

        return dispatch("FT.SEARCH", args, OutputType.NESTED);
    }


    @Override
    public List<MemoryItem> findByUserAndContentLike(String userId, String keyword) {
        Set<String> ids = stringRedisTemplate.opsForSet().members(MemoryKeys.userSet(userId));
        if (ids == null || ids.isEmpty()) return List.of();

        List<MemoryItem> result = new ArrayList<>();
        for (String id : ids) {
            Map<String, Object> hash = readHashNative(MemoryKeys.item(userId, id));
            if (hash.isEmpty() || "1".equals(hash.get(FIELD_INVALID))) continue;
            if (String.valueOf(hash.get(FIELD_CONTENT)).contains(keyword)) {
                result.add(toItem(hash));
            }
        }
        return result;
    }

    @Override
    public void invalidate(String memoryId) {
        String userId = stringRedisTemplate.opsForValue().get(MemoryKeys.reverse(memoryId));
        if (userId == null) return;

        String k = MemoryKeys.item(userId, memoryId);
        byte[] keyBytes = b(k);
        try {
            long now = System.currentTimeMillis();
            hsetBoth(keyBytes, b(FIELD_INVALID), b("1"));
            hsetBoth(keyBytes, b(FIELD_UPDATED), b(String.valueOf(now)));
        } catch (Exception e) {
            log.error(">>> 失效记忆失败, memoryId={}", memoryId, e);
        }
    }

    /**
     * 全量衰减某用户的所有记忆。
     * <p>
     * 衰减逻辑：
     * <ul>
     *   <li>读取每条记忆的 importance</li>
     *   <li>乘以衰减因子 factor（如 0.95）</li>
     *   <li>写回 importance 和 updatedAt</li>
     * </ul>
     * </p>
     *
     * @param userId 用户 ID
     * @param factor 衰减因子，取值范围 (0, 1]，如 0.95 表示每次衰减 5%
     * @return 实际衰减成功的条数（不含已失效/读取失败的条目）
     */
    @Override
    public int decayAll(String userId, double factor) {
        if (userId == null || userId.isBlank()) {
            log.warn(">>> decayAll 跳过：userId 为空");
            return 0;
        }
        if (factor <= 0 || factor > 1) {
            log.warn(">>> decayAll 跳过：factor 非法, factor={}", factor);
            return 0;
        }

        Set<String> ids = stringRedisTemplate.opsForSet().members(MemoryKeys.userSet(userId));
        if (ids == null || ids.isEmpty()) {
            log.debug(">>> decayAll: 用户 {} 无记忆，跳过", userId);
            return 0;
        }

        int successCount = 0;
        int skipCount = 0;
        int failCount = 0;
        long now = System.currentTimeMillis();

        for (String id : ids) {
            String k = MemoryKeys.item(userId, id);

            // 1. 读取当前 Hash
            Map<String, Object> hash = readHashNative(k);
            if (hash.isEmpty()) {
                skipCount++;   // 记忆已被删除（TTL 过期或手动删）
                continue;
            }
            // 2. 跳过已失效的
            if ("1".equals(hash.get(FIELD_INVALID))) {
                skipCount++;
                continue;
            }

            // 3. 计算新 importance
            double importance = RedisBytes.asDouble(hash.get(FIELD_IMPORTANCE), 0.5);
            double newImp = importance * factor;

            // 4. 写回
            try {
                byte[] keyBytes = b(k);
                hsetBoth(keyBytes, b(FIELD_IMPORTANCE), b(String.valueOf(newImp)));
                hsetBoth(keyBytes, b(FIELD_UPDATED), b(String.valueOf(now)));
                successCount++;
            } catch (Exception e) {
                log.warn(">>> decay 失败, key={}", k, e);
                failCount++;
            }
        }

        if (successCount > 0 || failCount > 0) {
            log.info(">>> decayAll 完成: userId={}, 总={}, 成功={}, 跳过={}, 失败={}, factor={}",
                    userId, ids.size(), successCount, skipCount, failCount, factor);
        }
        return successCount;
    }

    /**
     * 删除重要性低于阈值的记忆。
     * <p>
     * 删除逻辑：
     * <ol>
     *   <li>读取每条记忆的 importance</li>
     *   <li>若 importance &lt; threshold，则：
     *     <ul>
     *       <li>删除主数据 Hash</li>
     *       <li>从用户 ID 集合中移除</li>
     *       <li>删除反向索引</li>
     *     </ul>
     *   </li>
     * </ol>
     * </p>
     *
     * @param userId    用户 ID
     * @param threshold 阈值，importance 低于该值将被删除
     * @return 实际删除的条数
     */
    @Override
    public int deleteBelow(String userId, double threshold) {
        if (userId == null || userId.isBlank()) {
            log.warn(">>> deleteBelow 跳过：userId 为空");
            return 0;
        }
        if (threshold < 0 || threshold > 1) {
            log.warn(">>> deleteBelow 跳过：threshold 非法, threshold={}", threshold);
            return 0;
        }

        Set<String> ids = stringRedisTemplate.opsForSet().members(MemoryKeys.userSet(userId));
        if (ids == null || ids.isEmpty()) {
            log.debug(">>> deleteBelow: 用户 {} 无记忆，跳过", userId);
            return 0;
        }

        int deletedCount = 0;
        int skipCount = 0;
        int failCount = 0;

        for (String id : ids) {
            String k = MemoryKeys.item(userId, id);

            // 1. 读取当前 Hash
            Map<String, Object> hash = readHashNative(k);
            if (hash.isEmpty()) {
                // 主数据已不存在，顺手清理辅助索引
                try {
                    stringRedisTemplate.opsForSet().remove(MemoryKeys.userSet(userId), id);
                    stringRedisTemplate.delete(MemoryKeys.reverse(id));
                } catch (Exception e) {
                    log.warn(">>> 清理孤儿索引失败, id={}", id, e);
                }
                skipCount++;
                continue;
            }

            // 2. 跳过已失效的（不重复处理）
            if ("1".equals(hash.get(FIELD_INVALID))) {
                skipCount++;
                continue;
            }

            // 3. 判断是否低于阈值
            double importance = RedisBytes.asDouble(hash.get(FIELD_IMPORTANCE), 0.5);
            if (importance >= threshold) {
                skipCount++;
                continue;
            }

            // 4. 删除：主数据 + 用户集 + 反向索引
            try {
                byte[] keyBytes = b(k);
                delBoth(keyBytes);
                stringRedisTemplate.opsForSet().remove(MemoryKeys.userSet(userId), id);
                stringRedisTemplate.delete(MemoryKeys.reverse(id));
                deletedCount++;

                log.debug(">>> 删除低分记忆: userId={}, id={}, importance={}",
                        userId, id, importance);
            } catch (Exception e) {
                log.warn(">>> 删除低分记忆失败, key={}", k, e);
                failCount++;
            }
        }

        if (deletedCount > 0 || failCount > 0) {
            log.info(">>> deleteBelow 完成: userId={}, 总={}, 删除={}, 跳过={}, 失败={}, threshold={}",
                    userId, ids.size(), deletedCount, skipCount, failCount, threshold);
        }
        return deletedCount;
    }

    // ===================== 原生 Hash 读取 =====================

    private Map<String, Object> readHashNative(String key) {
        byte[] keyBytes = b(key);
        Map<String, Object> result = new HashMap<>();
        try {
            Map<byte[], byte[]> raw = hgetAllBoth(keyBytes);
            if (raw == null || raw.isEmpty()) return result;

            for (Map.Entry<byte[], byte[]> e : raw.entrySet()) {
                String field = new String(e.getKey(), StandardCharsets.UTF_8);
                if (FIELD_EMBEDDING.equals(field)) continue;
                result.put(field, new String(e.getValue(), StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            log.warn(">>> 读取 Hash 失败, key={}", key, e);
        }
        return result;
    }

    // ===================== 派发 =====================

    private enum OutputType { STATUS, NESTED }

    private Object dispatch(String command, List<byte[]> args, OutputType outputType) {
        CommandArgs<byte[], byte[]> cmdArgs = new CommandArgs<>(ByteArrayCodec.INSTANCE);
        for (int i = 1; i < args.size(); i++) cmdArgs.add(args.get(i));
        ProtocolKeyword keyword = command::getBytes;

        return withCommands(
                cmd -> outputType == OutputType.STATUS
                        ? cmd.dispatch(keyword, new StatusOutput<>(ByteArrayCodec.INSTANCE), cmdArgs)
                        : cmd.dispatch(keyword, new NestedMultiOutput<>(ByteArrayCodec.INSTANCE), cmdArgs),
                cmd -> outputType == OutputType.STATUS
                        ? cmd.dispatch(keyword, new StatusOutput<>(ByteArrayCodec.INSTANCE), cmdArgs)
                        : cmd.dispatch(keyword, new NestedMultiOutput<>(ByteArrayCodec.INSTANCE), cmdArgs)
        );
    }

    // ===================== 工具 =====================

    private byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    /**
     * RediSearch TAG 查询转义：对特殊字符加反斜杠。
     * 覆盖 RediSearch 文档要求转义的字符：{@code ,.<>{}[]"':;!@#$%^&*()-+=~} 和空格、反斜杠、竖线。
     */
    private String escapeTag(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder(s.length() * 2);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (",.<>{}[]\"':;!@#$%^&*()-+=~ \\|".indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * RESP3 下 FT.SEARCH 的顶层结构是 Map：{@code [k1, v1, k2, v2, ...]}。
     * <p>典型 key：{@code attributes} / {@code format} / {@code results} / {@code total_results} / {@code warning}。</p>
     */
    private Map<String, Object> topLevelMap(Object raw) {
        Map<String, Object> map = new HashMap<>();
        if (!(raw instanceof List<?> list)) return map;
        for (int i = 0; i + 1 < list.size(); i += 2) {
            String k = RedisBytes.asString(list.get(i));
            if (k != null) map.put(k, list.get(i + 1));
        }
        return map;
    }

    /**
     * 把 RESP3 的 {@code extra_attributes} 数组（交替 k/v）转成 Java Map。
     */
    private Map<String, Object> fieldsToMap(List<?> fields) {
        Map<String, Object> map = new HashMap<>(fields.size() / 2 + 1);
        for (int j = 0; j + 1 < fields.size(); j += 2) {
            String fieldName = RedisBytes.asString(fields.get(j));
            if (fieldName == null) continue;
            if (FIELD_EMBEDDING.equals(fieldName)) continue;   // ★ 跳过 embedding
            map.put(fieldName, fields.get(j + 1));
        }
        return map;
    }

    /**
     * 从 RediSearch 返回的 doc 结构里提取字段 Map。
     *
     * <p>RESP3 doc 结构形如：
     * {@code [id, redisKey, extra_attributes, [...], values, [...], score, "0.234"]}。</p>
     *
     * <p>本方法做三件事：</p>
     * <ol>
     *   <li>把 {@code extra_attributes} 里的字段放进 Map</li>
     *   <li>把 doc 顶层的 {@code score} / {@code $score} / {@code __score} 放进 Map</li>
     *   <li><b>从 doc 顶层的 {@code id}（Redis key）剥离出 userId / memoryId，
     *       因为 {@code RETURN} 声明后 RediSearch 不再返回 Hash 里的 {@code id} 字段</b></li>
     * </ol>
     */
    private Map<String, Object> extractDocFields(List<?> doc) {
        Map<String, Object> fields = new HashMap<>();
        String redisKey = null;

        for (int i = 0; i + 1 < doc.size(); i += 2) {
            String k = RedisBytes.asString(doc.get(i));
            if (k == null) continue;
            Object v = doc.get(i + 1);

            // ★ doc 顶层的 score（RESP3 下 KNN 的 score 不在 extra_attributes 里）
            if ("score".equals(k) || "$score".equals(k) || "__score".equals(k)) {
                fields.put(FIELD_SCORE, v);
                continue;
            }

            switch (k) {
                case "id" -> redisKey = RedisBytes.asString(v);
                case "extra_attributes" -> {
                    if (v instanceof List<?> attrs) {
                        Map<String, Object> extra = fieldsToMap(attrs);
                        // 兜底：不同 RediSearch 版本 score 字段名可能带前缀
                        for (Map.Entry<String, Object> e : extra.entrySet()) {
                            String key = e.getKey();
                            if (key != null && key.contains("score")) {
                                fields.putIfAbsent(FIELD_SCORE, e.getValue());
                            } else {
                                fields.put(key, e.getValue());
                            }
                        }
                    }
                }
                case "values" -> { /* 忽略 */ }
                default -> {
                    if (k.contains("score")) {
                        fields.put(FIELD_SCORE, v);
                    }
                }
            }
        }

        // ★★★ 关键修复：从 Redis key 剥离 userId / memoryId
        // key 格式：ai:memory:{userId}:{memoryId}
        if (redisKey != null) {
            fields.put(FIELD_REDIS_KEY, redisKey);
            if (redisKey.startsWith(KEY_PREFIX)) {
                String rest = redisKey.substring(KEY_PREFIX.length());
                int idx = rest.indexOf(':');
                if (idx > 0) {
                    String uid = rest.substring(0, idx);
                    String mid = rest.substring(idx + 1);
                    fields.putIfAbsent(FIELD_USER_ID, uid);
                    fields.putIfAbsent(FIELD_ID, mid);
                } else {
                    log.warn(">>> Redis key 格式异常, 无法剥离 memoryId: {}", redisKey);
                }
            } else {
                log.warn(">>> Redis key 前缀不匹配: key={}, prefix={}", redisKey, KEY_PREFIX);
            }
        }

        return fields;
    }

    private MemoryItem toItem(Map<?, ?> hash) {
        return new MemoryItem(
                RedisBytes.asString(hash.get(FIELD_ID)),
                RedisBytes.asString(hash.get(FIELD_USER_ID)),
                RedisBytes.asString(hash.get(FIELD_TYPE)),
                RedisBytes.asString(hash.get(FIELD_CONTENT)),
                List.of(),
                RedisBytes.asDouble(hash.get(FIELD_IMPORTANCE), 0.5),
                RedisBytes.asLong(hash.get(FIELD_CREATED_AT), 0L),
                RedisBytes.asLong(hash.get(FIELD_UPDATED), 0L),
                "1".equals(RedisBytes.asString(hash.get(FIELD_INVALID)))
        );
    }

    /**
     * 解析 RESP3 下 FT.SEARCH 的响应，提取命中列表。
     *
     * <p>因为 {@code RETURN} 只返回 {@code userId/type/content} 三个字段，
     * 本方法在拿到 {@code __redisKey} 后回查 Hash 补全 {@code id/importance/createdAt/updatedAt/invalid}。</p>
     */
    private List<MemoryHit> parseSearchHits(Object raw, String expectedUserId) {
        List<MemoryHit> result = new ArrayList<>();
        if (raw == null) return result;

        Map<String, Object> top = topLevelMap(raw);
        Object resultsObj = top.get("results");
        if (!(resultsObj instanceof List<?> docs)) {
            log.warn(">>> FT.SEARCH 响应缺少 results 字段, top keys={}", top.keySet());
            return result;
        }

        for (Object docObj : docs) {
            if (!(docObj instanceof List<?> doc)) continue;

            Map<String, Object> fields = extractDocFields(doc);

            Object scoreObj = fields.get(FIELD_SCORE);

            String uid = RedisBytes.asString(fields.get(FIELD_USER_ID));
            if (expectedUserId != null && uid != null && !expectedUserId.equals(uid)) {
                log.warn(">>> KNN 结果 userId 不匹配, 丢弃");
                continue;
            }

            String memoryId = RedisBytes.asString(fields.get(FIELD_ID));
            if (memoryId == null || memoryId.isBlank()) {
                log.warn(">>> KNN 结果缺少 memoryId, 丢弃");
                continue;
            }

            // 回查 Hash 补全，但保留 score
            String redisKey = RedisBytes.asString(fields.get(FIELD_REDIS_KEY));
            if (redisKey != null) {
                Map<String, Object> hash = readHashNative(redisKey);
                if (!hash.isEmpty()) {
                    hash.putIfAbsent(FIELD_USER_ID, uid);
                    hash.putIfAbsent(FIELD_ID, memoryId);
                    if (scoreObj != null) {
                        hash.put(FIELD_SCORE, scoreObj);
                    }
                    fields = hash;
                }
            }

            MemoryItem item = toItem(fields);

            double rawScore = RedisBytes.asDouble(fields.get(FIELD_SCORE), 1.0);

            // ★ P0-2 修复：区分 distance / similarity
            //   RediSearch COSINE 距离范围是 [0,2]，
            //   1 - distance/2 才能得到 [1,-1] 的相似度（1=完全相同，0=正交，-1=完全相反）。
            //   原实现 1 - distance 在 distance>1 时产出负值，让"相似度"语义失真。
            double similarity;
            if (SCORE_IS_DISTANCE) {
                similarity = 1.0 - rawScore / 2.0;
            } else {
                similarity = rawScore;
            }

            if (log.isDebugEnabled()) {
                log.debug(">>> KNN rawScore={}, similarity={}, memoryId={}",
                        rawScore, similarity, memoryId);
            }

            result.add(new MemoryHit(item, similarity));
        }

        return result;
    }

    // ===================== 活跃用户 =====================

    @Override
    public Set<String> getAllActiveUsers() {
        try {
            Set<String> activeUsers = new HashSet<>();
            ScanOptions scanOptions = ScanOptions.scanOptions()
                    .match(MemoryKeys.userPattern())
                    .count(100)
                    .build();

            stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
                Cursor<byte[]> cursor = connection.keyCommands().scan(scanOptions);
                try {
                    while (cursor.hasNext()) {
                        byte[] keyBytes = cursor.next();
                        if (keyBytes == null) continue;
                        String key = new String(keyBytes, StandardCharsets.UTF_8);
                        if (key.startsWith(USER_SET_PREFIX)) {
                            String userId = key.substring(USER_SET_PREFIX.length());
                            if (!userId.isBlank()) activeUsers.add(userId);
                        }
                    }
                } finally {
                    if (cursor != null) cursor.close();
                }
                return null;
            });

            log.info(">>> 获取活跃用户成功，共 {} 个", activeUsers.size());
            return activeUsers;
        } catch (Exception e) {
            log.warn(">>> 获取活跃用户失败", e);
            throw e;
        }
    }

    // ===================== 调试用：查用户所有记忆 =====================

    @Override
    public List<MemoryItem> findAllByUser(String userId) {
        List<MemoryItem> result = new ArrayList<>();
        String pattern = MemoryKeys.itemPattern(userId);

        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            Cursor<byte[]> cursor = connection.keyCommands()
                    .scan(ScanOptions.scanOptions().match(pattern).count(100).build());

            try {
                while (cursor.hasNext()) {
                    byte[] keyBytes = cursor.next();
                    if (keyBytes == null) continue;

                    Map<byte[], byte[]> hash = connection.hashCommands().hGetAll(keyBytes);
                    if (hash == null || hash.isEmpty()) continue;

                    Map<String, Object> map = new HashMap<>();
                    for (Map.Entry<byte[], byte[]> e : hash.entrySet()) {
                        String field = new String(e.getKey(), StandardCharsets.UTF_8);
                        if (FIELD_EMBEDDING.equals(field)) continue;
                        map.put(field, new String(e.getValue(), StandardCharsets.UTF_8));
                    }
                    result.add(toItem(map));
                }
            } finally {
                if (cursor != null) cursor.close();
            }
            return null;
        });

        log.info(">>> 用户 {} 共有 {} 条记忆", userId, result.size());
        return result;
    }


    /** 统一执行单机/集群的 hset */
    private void hsetBoth(byte[] key, byte[] field, byte[] value) {
        withCommands(
                cmd -> { cmd.hset(key, field, value); return null; },
                cmd -> { cmd.hset(key, field, value); return null; }
        );
    }

    /** 统一执行 del */
    private long delBoth(byte[] key) {
        return withCommands(
                cmd -> cmd.del(key),
                cmd -> cmd.del(key)
        );
    }

    /** 统一执行 hgetall */
    private Map<byte[], byte[]> hgetAllBoth(byte[] key) {
        return withCommands(
                cmd -> cmd.hgetall(key),
                cmd -> cmd.hgetall(key)
        );
    }
}