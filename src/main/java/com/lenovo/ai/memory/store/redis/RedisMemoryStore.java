package com.lenovo.ai.memory.store.redis;

import com.lenovo.ai.memory.model.MemoryHit;
import com.lenovo.ai.memory.model.MemoryItem;
import com.lenovo.ai.memory.store.MemoryMaintenanceStore;
import com.lenovo.ai.memory.store.MemoryStore;
import com.lenovo.ai.memory.util.RedisBytes;
import com.lenovo.ai.memory.util.RedisValueUtils;
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
import java.util.function.BiConsumer;
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
                throw new IllegalStateException(String.format(
                        "向量维度配置错误：配置=%d, 实际=%d, 请修改后重启",
                        vectorDim, actualDim));
            }
            log.info(">>> 向量维度校验通过: {}", vectorDim);
        } catch (Exception e) {
            log.error(">>> 向量维度校验失败", e);
            throw e;
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

    // ===================== 保存 =====================

    @Override
    public void save(MemoryItem item) {
        String key = MemoryKeys.item(item.userId(), item.id());
        byte[] keyBytes = b(key);
        byte[] embeddingBytes = VectorUtils.toFloatBytes(item.embedding());

        try {
            withCommands(
                    cmd -> {
                        writeFields((k, v) -> cmd.hset(keyBytes, k, v), item, embeddingBytes);
                        cmd.expire(keyBytes, TTL_SECONDS);
                        return null;
                    },
                    cmd -> {
                        writeFields((k, v) -> cmd.hset(keyBytes, k, v), item, embeddingBytes);
                        cmd.expire(keyBytes, TTL_SECONDS);
                        return null;
                    }
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
            stringRedisTemplate.opsForValue().set(reverseKey, item.userId(), TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error(">>> 保存记忆辅助索引失败, userId={}, id={}", item.userId(), item.id(), e);
        }
    }

    private void writeFields(BiConsumer<byte[], byte[]> hset, MemoryItem item, byte[] emb) {
        hset.accept(b(FIELD_ID), b(item.id()));
        hset.accept(b(FIELD_USER_ID), b(item.userId()));
        hset.accept(b(FIELD_TYPE), b(item.type()));
        hset.accept(b(FIELD_CONTENT), b(item.content() == null ? "" : item.content()));
        hset.accept(b(FIELD_IMPORTANCE), b(String.valueOf(item.importance())));
        hset.accept(b(FIELD_CREATED_AT), b(String.valueOf(item.createdAt())));
        hset.accept(b(FIELD_UPDATED), b(String.valueOf(item.updatedAt())));
        hset.accept(b(FIELD_INVALID), b(item.invalid() ? "1" : "0"));
        if (emb != null && emb.length > 0) {
            hset.accept(b(FIELD_EMBEDDING), emb);
        }
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
        // ★ 显式 RETURN：只返回业务字段，避免拉回 embedding（4KB/条）
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

    // ===================== 其他实现 =====================

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
            withCommands(
                    cmd -> {
                        cmd.hset(keyBytes, b(FIELD_INVALID), b("1"));
                        cmd.hset(keyBytes, b(FIELD_UPDATED), b(String.valueOf(now)));
                        return null;
                    },
                    cmd -> {
                        cmd.hset(keyBytes, b(FIELD_INVALID), b("1"));
                        cmd.hset(keyBytes, b(FIELD_UPDATED), b(String.valueOf(now)));
                        return null;
                    }
            );
        } catch (Exception e) {
            log.error(">>> 失效记忆失败, memoryId={}", memoryId, e);
        }
    }

    @Override
    public void decayAll(String userId, double factor) {
        Set<String> ids = stringRedisTemplate.opsForSet().members(MemoryKeys.userSet(userId));
        if (ids == null || ids.isEmpty()) return;

        for (String id : ids) {
            String k = MemoryKeys.item(userId, id);
            byte[] keyBytes = b(k);
            Map<String, Object> hash = readHashNative(k);
            if (hash.isEmpty() || "1".equals(hash.get(FIELD_INVALID))) continue;

            double importance = RedisValueUtils.parseDouble(hash.get(FIELD_IMPORTANCE), 0.5);
            try {
                long now = System.currentTimeMillis();
                double newImp = importance * factor;
                withCommands(
                        cmd -> {
                            cmd.hset(keyBytes, b(FIELD_IMPORTANCE), b(String.valueOf(newImp)));
                            cmd.hset(keyBytes, b(FIELD_UPDATED), b(String.valueOf(now)));
                            return null;
                        },
                        cmd -> {
                            cmd.hset(keyBytes, b(FIELD_IMPORTANCE), b(String.valueOf(newImp)));
                            cmd.hset(keyBytes, b(FIELD_UPDATED), b(String.valueOf(now)));
                            return null;
                        }
                );
            } catch (Exception e) {
                log.warn(">>> decay 失败, key={}", k, e);
            }
        }
    }

    @Override
    public void deleteBelow(String userId, double threshold) {
        Set<String> ids = stringRedisTemplate.opsForSet().members(MemoryKeys.userSet(userId));
        if (ids == null || ids.isEmpty()) return;

        for (String id : ids) {
            String k = MemoryKeys.item(userId, id);
            Map<String, Object> hash = readHashNative(k);
            if (hash.isEmpty() || "1".equals(hash.get(FIELD_INVALID))) continue;

            double importance = RedisValueUtils.parseDouble(hash.get(FIELD_IMPORTANCE), 0.5);
            if (importance < threshold) {
                try {
                    withCommands(
                            cmd -> cmd.del(b(k)),
                            cmd -> cmd.del(b(k))
                    );
                    stringRedisTemplate.opsForSet().remove(MemoryKeys.userSet(userId), id);
                    stringRedisTemplate.delete(MemoryKeys.reverse(id));
                } catch (Exception e) {
                    log.warn(">>> 删除低分记忆失败, key={}", k, e);
                }
            }
        }
    }

    // ===================== 原生 Hash 读取 =====================

    private Map<String, Object> readHashNative(String key) {
        byte[] keyBytes = b(key);
        Map<String, Object> result = new HashMap<>();
        try {
            Map<byte[], byte[]> raw = withCommands(
                    cmd -> cmd.hgetall(keyBytes),
                    cmd -> cmd.hgetall(keyBytes)
            );
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
                RedisValueUtils.parseDouble(hash.get(FIELD_IMPORTANCE), 0.5),
                RedisValueUtils.parseLong(hash.get(FIELD_CREATED_AT), 0L),
                RedisValueUtils.parseLong(hash.get(FIELD_UPDATED), 0L),
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

            // 防御性校验 userId
            String uid = RedisBytes.asString(fields.get(FIELD_USER_ID));
            if (expectedUserId != null && uid != null && !expectedUserId.equals(uid)) {
                log.warn(">>> KNN 结果 userId 不匹配, 期望={}, 实际={}, 丢弃", expectedUserId, uid);
                continue;
            }

            String memoryId = RedisBytes.asString(fields.get(FIELD_ID));
            if (memoryId == null || memoryId.isBlank()) {
                log.warn(">>> KNN 结果缺少 memoryId, 丢弃。fields keys={}", fields.keySet());
                continue;
            }

            // ★ 回查 Hash 补全 importance/createdAt/updatedAt/invalid
            String redisKey = RedisBytes.asString(fields.get(FIELD_REDIS_KEY));
            if (redisKey != null) {
                Map<String, Object> hash = readHashNative(redisKey);
                if (!hash.isEmpty()) {
                    // Hash 里的字段更全、更权威，覆盖 RESP3 返回的部分字段
                    hash.putIfAbsent(FIELD_USER_ID, uid);
                    hash.putIfAbsent(FIELD_ID, memoryId);
                    fields = hash;
                }
            }

            MemoryItem item = toItem(fields);

            // score 是 KNN 的 COSINE distance（越小越相似），转成 similarity
            double distance = RedisValueUtils.parseDouble(fields.get(FIELD_SCORE), 1.0);
            double similarity = 1.0 - distance;
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
}