package com.lenovo.ai.memory;

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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

@Slf4j
@Component
public class RedisMemoryStore implements MemoryStore {

    private static final String INDEX_NAME = "idx:ai:memory";
    private static final String KEY_PREFIX = "ai:memory:";
    private static final String USER_SET_PREFIX = "ai:memory:user:";
    private static final String REVERSE_PREFIX = "ai:memory:reverse:";
    private static final long TTL_SECONDS = TimeUnit.DAYS.toSeconds(90);

    @Value("${ai.embedding.dimension:1536}")
    private int vectorDim;

    private final RedisTemplate<Object, Object> redisTemplate;
    private final LettuceConnectionFactory connectionFactory;

    private StatefulRedisConnection<byte[], byte[]> nativeConnection;
    private StatefulRedisClusterConnection<byte[], byte[]> nativeClusterConnection;

    public RedisMemoryStore(RedisTemplate<Object, Object> redisTemplate,
                            LettuceConnectionFactory connectionFactory) {
        this.redisTemplate = redisTemplate;
        this.connectionFactory = connectionFactory;
    }

    @PostConstruct
    public void init() {
        initNativeConnection();
        initIndex();
    }

    private void initNativeConnection() {
        try {
            Object client = connectionFactory.getNativeClient();
            log.info(">>> nativeClient 类型: {}", client == null ? "null" : client.getClass().getName());

            if (client instanceof RedisClient rc) {
                nativeConnection = rc.connect(ByteArrayCodec.INSTANCE);
                log.info(">>> 已建立原生 Lettuce 单机连接");
            } else if (client instanceof RedisClusterClient rcc) {
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
            args.add(b("ON"));            args.add(b("HASH"));
            args.add(b("PREFIX"));        args.add(b("1")); args.add(b(KEY_PREFIX));
            args.add(b("SCHEMA"));
            args.add(b("userId"));        args.add(b("TAG"));
            args.add(b("type"));          args.add(b("TAG"));
            args.add(b("content"));       args.add(b("TEXT"));
            args.add(b("importance"));    args.add(b("NUMERIC"));
            args.add(b("createdAt"));     args.add(b("NUMERIC"));
            args.add(b("updatedAt"));     args.add(b("NUMERIC"));
            args.add(b("invalid"));       args.add(b("TAG"));
            args.add(b("embedding"));     args.add(b("VECTOR"));
            args.add(b("HNSW"));          args.add(b("6"));
            args.add(b("TYPE"));          args.add(b("FLOAT32"));
            args.add(b("DIM"));           args.add(b(String.valueOf(vectorDim)));
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

    @PreDestroy
    public void closeNativeConnection() {
        try { if (nativeConnection != null) nativeConnection.close(); } catch (Exception ignore) {}
        try { if (nativeClusterConnection != null) nativeClusterConnection.close(); } catch (Exception ignore) {}
    }

    // ===================== 保存 =====================

    @Override
    public void save(MemoryItem item) {
        String key = key(item.userId(), item.id());
        byte[] keyBytes = b(key);
        byte[] embeddingBytes = toFloatBytes(item.embedding());

        try {
            if (nativeConnection != null) {
                RedisCommands<byte[], byte[]> cmd = nativeConnection.sync();
                writeFields((k, v) -> cmd.hset(keyBytes, k, v), item, embeddingBytes);
                cmd.expire(keyBytes, TTL_SECONDS);
            } else if (nativeClusterConnection != null) {
                RedisAdvancedClusterCommands<byte[], byte[]> cmd = nativeClusterConnection.sync();
                writeFields((k, v) -> cmd.hset(keyBytes, k, v), item, embeddingBytes);
                cmd.expire(keyBytes, TTL_SECONDS);
            } else {
                log.warn(">>> save: 原生连接未初始化");
            }
        } catch (Exception e) {
            log.error(">>> 保存记忆失败, key={}", key, e);
            return;
        }

        redisTemplate.opsForSet().add(USER_SET_PREFIX + item.userId(), item.id());
        redisTemplate.expire(USER_SET_PREFIX + item.userId(), 90, TimeUnit.DAYS);
        redisTemplate.opsForValue().set(REVERSE_PREFIX + item.id(), item.userId(), 90, TimeUnit.DAYS);
    }

    /** ✅ 统一字段写入，单机/集群共用 */
    private void writeFields(BiConsumer<byte[], byte[]> hset, MemoryItem item, byte[] emb) {
        hset.accept(b("id"), b(item.id()));
        hset.accept(b("userId"), b(item.userId()));
        hset.accept(b("type"), b(item.type()));
        hset.accept(b("content"), b(item.content() == null ? "" : item.content()));
        hset.accept(b("importance"), b(String.valueOf(item.importance())));
        hset.accept(b("createdAt"), b(String.valueOf(item.createdAt())));
        hset.accept(b("updatedAt"), b(String.valueOf(item.updatedAt())));
        hset.accept(b("invalid"), b(item.invalid() ? "1" : "0"));
        if (emb != null && emb.length > 0) {
            hset.accept(b("embedding"), emb);
        }
    }

    // ===================== 向量检索 =====================

    @Override
    public List<MemoryItem> searchByUser(String userId, List<Double> queryEmbedding, int topK) {
        if (queryEmbedding == null || queryEmbedding.isEmpty()) {
            log.warn(">>> 向量召回跳过：queryEmbedding 为空, userId={}", userId);
            return List.of();
        }
        if (queryEmbedding.size() != vectorDim) {
            log.warn(">>> 向量召回跳过：维度不符, 期望={}, 实际={}", vectorDim, queryEmbedding.size());
            return List.of();
        }

        byte[] qBytes = toFloatBytes(queryEmbedding);
        String query = String.format(
                "(@userId:{%s} @invalid:{0})=>[KNN %d @embedding $vec AS score]",
                escapeTag(userId), topK
        );

        try {
            List<byte[]> args = new ArrayList<>();
            args.add(b("FT.SEARCH"));
            args.add(b(INDEX_NAME));
            args.add(b(query));
            args.add(b("PARAMS"));   args.add(b("2"));
            args.add(b("vec"));      args.add(qBytes);
            args.add(b("SORTBY"));   args.add(b("score"));
            args.add(b("DIALECT"));  args.add(b("2"));
            args.add(b("LIMIT"));    args.add(b("0")); args.add(b(String.valueOf(topK)));

            Object raw = dispatch("FT.SEARCH", args, OutputType.NESTED);
            return parseSearchResult(raw);
        } catch (Exception e) {
            log.error(">>> 向量召回失败, userId={}", userId, e);
            return List.of();
        }
    }

    // ===================== 其他实现 =====================

    @Override
    public List<MemoryItem> findByUserAndContentLike(String userId, String keyword) {
        Set<Object> ids = redisTemplate.opsForSet().members(USER_SET_PREFIX + userId);
        if (ids == null) return List.of();
        List<MemoryItem> result = new ArrayList<>();
        for (Object id : ids) {
            Map<Object, Object> hash = redisTemplate.opsForHash().entries(key(userId, String.valueOf(id)));
            if (hash.isEmpty() || "1".equals(hash.get("invalid"))) continue;
            if (String.valueOf(hash.get("content")).contains(keyword)) result.add(toItem(hash));
        }
        return result;
    }

    @Override
    public void invalidate(String memoryId) {
        Object userIdObj = redisTemplate.opsForValue().get(REVERSE_PREFIX + memoryId);
        if (userIdObj == null) return;
        String k = key(String.valueOf(userIdObj), memoryId);
        redisTemplate.opsForHash().put(k, "invalid", "1");
        redisTemplate.opsForHash().put(k, "updatedAt", System.currentTimeMillis());
    }

    @Override
    public void decayAll(String userId, double factor) {
        Set<Object> ids = redisTemplate.opsForSet().members(USER_SET_PREFIX + userId);
        if (ids == null) return;
        for (Object id : ids) {
            String k = key(userId, String.valueOf(id));
            Map<Object, Object> hash = redisTemplate.opsForHash().entries(k);
            if (hash.isEmpty() || "1".equals(hash.get("invalid"))) continue;
            double importance = parseDouble(hash.get("importance"), 0.5);
            redisTemplate.opsForHash().put(k, "importance", importance * factor);
            redisTemplate.opsForHash().put(k, "updatedAt", System.currentTimeMillis());
        }
    }

    @Override
    public void deleteBelow(String userId, double threshold) {
        Set<Object> ids = redisTemplate.opsForSet().members(USER_SET_PREFIX + userId);
        if (ids == null) return;
        for (Object id : ids) {
            String k = key(userId, String.valueOf(id));
            Map<Object, Object> hash = redisTemplate.opsForHash().entries(k);
            if (hash.isEmpty() || "1".equals(hash.get("invalid"))) continue;
            double importance = parseDouble(hash.get("importance"), 0.5);
            if (importance < threshold) {
                redisTemplate.delete(k);
                redisTemplate.opsForSet().remove(USER_SET_PREFIX + userId, id);
                redisTemplate.delete(REVERSE_PREFIX + id);
            }
        }
    }

    // ===================== 派发 =====================

    private enum OutputType { STATUS, NESTED }

    private Object dispatch(String command, List<byte[]> args, OutputType outputType) {
        CommandArgs<byte[], byte[]> cmdArgs = new CommandArgs<>(ByteArrayCodec.INSTANCE);
        for (int i = 1; i < args.size(); i++) cmdArgs.add(args.get(i));
        ProtocolKeyword keyword = command::getBytes;

        if (nativeConnection != null) {
            RedisCommands<byte[], byte[]> commands = nativeConnection.sync();
            return outputType == OutputType.STATUS
                    ? commands.dispatch(keyword, new StatusOutput<>(ByteArrayCodec.INSTANCE), cmdArgs)
                    : commands.dispatch(keyword, new NestedMultiOutput<>(ByteArrayCodec.INSTANCE), cmdArgs);
        }
        if (nativeClusterConnection != null) {
            RedisAdvancedClusterCommands<byte[], byte[]> commands = nativeClusterConnection.sync();
            return outputType == OutputType.STATUS
                    ? commands.dispatch(keyword, new StatusOutput<>(ByteArrayCodec.INSTANCE), cmdArgs)
                    : commands.dispatch(keyword, new NestedMultiOutput<>(ByteArrayCodec.INSTANCE), cmdArgs);
        }
        throw new IllegalStateException("原生 Lettuce 连接未初始化");
    }

    // ===================== 工具 =====================

    private byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    private String key(String userId, String memoryId) {
        return KEY_PREFIX + userId + ":" + memoryId;
    }

    private String escapeTag(String s) {
        return s.replaceAll("([,\\.<>\\-\\[\\]{}()\"'`~!@#$%^&*+=|;:/?\\\\ ])", "\\\\$1");
    }

    private byte[] toFloatBytes(List<Double> vec) {
        if (vec == null || vec.isEmpty()) return new byte[0];
        ByteBuffer buf = ByteBuffer.allocate(vec.size() * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (Double d : vec) buf.putFloat(d.floatValue());
        return buf.array();
    }

    /** ✅ 统一的 MemoryItem 构造 */
    private MemoryItem toItem(Map<?, ?> hash) {
        return new MemoryItem(
                String.valueOf(hash.get("id")),
                String.valueOf(hash.get("userId")),
                String.valueOf(hash.get("type")),
                String.valueOf(hash.get("content")),
                List.of(),
                parseDouble(hash.get("importance"), 0.5),
                parseLong(hash.get("createdAt"), 0L),
                parseLong(hash.get("updatedAt"), 0L),
                "1".equals(String.valueOf(hash.get("invalid")))
        );
    }

    private double parseDouble(Object o, double def) {
        if (o == null) return def;
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private long parseLong(Object o, long def) {
        if (o == null) return def;
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    private List<MemoryItem> parseSearchResult(Object raw) {
        List<MemoryItem> result = new ArrayList<>();
        if (!(raw instanceof List<?> list) || list.isEmpty()) return result;

        int i = 1;
        while (i + 1 < list.size()) {
            Object fieldsObj = list.get(i + 1);
            if (fieldsObj instanceof List<?> fields) {
                Map<String, Object> map = new HashMap<>();
                for (int j = 0; j + 1 < fields.size(); j += 2) {
                    map.put(String.valueOf(fields.get(j)), fields.get(j + 1));
                }
                if (map.get("id") != null) result.add(toItem(map));
            }
            i += 2;
        }
        return result;
    }

    @Override
    public Set<String> getAllActiveUsers() {
        try {
            // 使用 RedisTemplate 的 scan 方法扫描所有 USER_SET_PREFIX 开头的 key
            java.util.Set<String> activeUsers = new java.util.HashSet<>();
            String pattern = USER_SET_PREFIX + "*";
            
            // 配置扫描选项
            ScanOptions scanOptions = 
                ScanOptions.scanOptions()
                    .match(pattern)
                    .count(100)
                    .build();
            
            // 使用 RedisTemplate 的 scan 进行遍历，返回 Cursor
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                Cursor<byte[]> cursor = 
                    connection.keyCommands().scan(scanOptions);
                
                try {
                    while (cursor.hasNext()) {
                        byte[] keyBytes = cursor.next();
                        if (keyBytes != null) {
                            String key = new String(keyBytes, StandardCharsets.UTF_8);
                            if (key.startsWith(USER_SET_PREFIX)) {
                                String userId = key.substring(USER_SET_PREFIX.length());
                                if (!userId.isBlank()) {
                                    activeUsers.add(userId);
                                }
                            }
                        }
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
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
}