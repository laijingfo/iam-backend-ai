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
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    /** 主数据（Hash）用原生 Lettuce，裸字节读写 */
    private final LettuceConnectionFactory connectionFactory;
    /** 辅助 key（Set / String）统一用 StringRedisTemplate，序列化器为 String */
    private final StringRedisTemplate stringRedisTemplate;

    private StatefulRedisConnection<byte[], byte[]> nativeConnection;
    private StatefulRedisClusterConnection<byte[], byte[]> nativeClusterConnection;

    public RedisMemoryStore(LettuceConnectionFactory connectionFactory,
                            StringRedisTemplate stringRedisTemplate) {
        this.connectionFactory = connectionFactory;
        this.stringRedisTemplate = stringRedisTemplate;
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
    public void save(MemoryItem item) {                                             // 保存记忆
        String key = key(item.userId(), item.id());                                 // 1. 构造主键
        byte[] keyBytes = b(key);                                                   //    转字节
        byte[] embeddingBytes = toFloatBytes(item.embedding());                     // 2. 向量转 float32 字节

        // 1. 主数据：原生 Lettuce 写 Hash                                         // 注释
        try {                                                                       // 3. try
            if (nativeConnection != null) {                                         //    单机连接
                RedisCommands<byte[], byte[]> cmd = nativeConnection.sync();        //    同步命令
                writeFields((k, v) -> cmd.hset(keyBytes, k, v), item, embeddingBytes);//    写字段
                cmd.expire(keyBytes, TTL_SECONDS);                                  //    设置 TTL
            } else if (nativeClusterConnection != null) {                           //    集群连接
                RedisAdvancedClusterCommands<byte[], byte[]> cmd = nativeClusterConnection.sync(); // 同步
                writeFields((k, v) -> cmd.hset(keyBytes, k, v), item, embeddingBytes);//    写字段
                cmd.expire(keyBytes, TTL_SECONDS);                                  //    设置 TTL
            } else {                                                                //    未初始化
                log.warn(">>> save: 原生连接未初始化");                              //    日志
                return;                                                             //    返回
            }
        } catch (Exception e) {                                                     // 4. 异常
            log.error(">>> 保存记忆主数据失败, key={}", key, e);                      //    日志
            return;                                                                 //    返回
        }

        // 2. 辅助索引：StringRedisTemplate，纯字符串                               // 注释
        try {                                                                       // 5. try
            String userSetKey = USER_SET_PREFIX + item.userId();                    //    用户集合 key
            String reverseKey = REVERSE_PREFIX + item.id();                         //    反向索引 key

            stringRedisTemplate.opsForSet().add(userSetKey, item.id());             //    加入用户集合
            stringRedisTemplate.expire(userSetKey, TTL_SECONDS, TimeUnit.SECONDS);  //    设置 TTL

            stringRedisTemplate.opsForValue().set(reverseKey, item.userId(), TTL_SECONDS, TimeUnit.SECONDS); // 写反向索引
        } catch (Exception e) {                                                     // 6. 异常
            log.error(">>> 保存记忆辅助索引失败, userId={}, id={}", item.userId(), item.id(), e); // 日志
        }
    }

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

    @Override                                                                       // 实现接口
    public List<MemoryItem> searchByUser(String userId, List<Double> queryEmbedding, int topK) { // KNN 检索
        if (queryEmbedding == null || queryEmbedding.isEmpty()) {                   // 1. 向量空
            log.warn(">>> 向量召回跳过：queryEmbedding 为空, userId={}", userId);     //    日志
            return List.of();                                                       //    返回空
        }
        if (queryEmbedding.size() != vectorDim) {                                   // 2. 维度不符
            log.warn(">>> 向量召回跳过：维度不符, 期望={}, 实际={}", vectorDim, queryEmbedding.size()); // 日志
            return List.of();                                                       //    返回空
        }

        byte[] qBytes = toFloatBytes(queryEmbedding);                               // 3. 向量转字节
        String query = String.format(                                               // 4. 构造查询
                "(@userId:{%s} @invalid:{0})=>[KNN %d @embedding $vec AS score]",   //    TAG 过滤 + KNN
                escapeTag(userId), topK                                             //    用户 ID 转义 + topK
        );

        try {                                                                       // 5. try
            List<byte[]> args = new ArrayList<>();                                  //    参数列表
            args.add(b("FT.SEARCH"));                                               //    命令
            args.add(b(INDEX_NAME));                                                //    索引名
            args.add(b(query));                                                     //    查询
            args.add(b("PARAMS"));   args.add(b("2"));                              //    PARAMS 2
            args.add(b("vec"));      args.add(qBytes);                              //    参数 vec
            args.add(b("SORTBY"));   args.add(b("score"));                          //    排序
            args.add(b("DIALECT"));  args.add(b("2"));                              //    DIALECT 2
            args.add(b("LIMIT"));    args.add(b("0")); args.add(b(String.valueOf(topK))); // LIMIT

            Object raw = dispatch("FT.SEARCH", args, OutputType.NESTED);            //    派发命令
            return parseSearchResult(raw);                                          //    解析结果
        } catch (Exception e) {                                                     // 6. 异常
            log.error(">>> 向量召回失败, userId={}", userId, e);                      //    日志
            return List.of();                                                       //    返回空
        }
    }

    // ===================== 其他实现 =====================

    @Override
    public List<MemoryItem> findByUserAndContentLike(String userId, String keyword) {
        Set<String> ids = stringRedisTemplate.opsForSet().members(USER_SET_PREFIX + userId);
        if (ids == null || ids.isEmpty()) return List.of();

        List<MemoryItem> result = new ArrayList<>();
        for (String id : ids) {
            Map<String, Object> hash = readHashNative(key(userId, id));
            if (hash.isEmpty() || "1".equals(hash.get("invalid"))) continue;
            if (String.valueOf(hash.get("content")).contains(keyword)) {
                result.add(toItem(hash));
            }
        }
        return result;
    }

    @Override
    public void invalidate(String memoryId) {
        String userId = stringRedisTemplate.opsForValue().get(REVERSE_PREFIX + memoryId);
        if (userId == null) return;

        String k = key(userId, memoryId);
        byte[] keyBytes = b(k);
        try {
            if (nativeConnection != null) {
                RedisCommands<byte[], byte[]> cmd = nativeConnection.sync();
                cmd.hset(keyBytes, b("invalid"), b("1"));
                cmd.hset(keyBytes, b("updatedAt"), b(String.valueOf(System.currentTimeMillis())));
            } else if (nativeClusterConnection != null) {
                RedisAdvancedClusterCommands<byte[], byte[]> cmd = nativeClusterConnection.sync();
                cmd.hset(keyBytes, b("invalid"), b("1"));
                cmd.hset(keyBytes, b("updatedAt"), b(String.valueOf(System.currentTimeMillis())));
            }
        } catch (Exception e) {
            log.error(">>> 失效记忆失败, memoryId={}", memoryId, e);
        }
    }

    @Override
    public void decayAll(String userId, double factor) {
        Set<String> ids = stringRedisTemplate.opsForSet().members(USER_SET_PREFIX + userId);
        if (ids == null || ids.isEmpty()) return;

        for (String id : ids) {
            String k = key(userId, id);
            byte[] keyBytes = b(k);
            Map<String, Object> hash = readHashNative(k);
            if (hash.isEmpty() || "1".equals(hash.get("invalid"))) continue;

            double importance = parseDouble(hash.get("importance"), 0.5);
            try {
                if (nativeConnection != null) {
                    RedisCommands<byte[], byte[]> cmd = nativeConnection.sync();
                    cmd.hset(keyBytes, b("importance"), b(String.valueOf(importance * factor)));
                    cmd.hset(keyBytes, b("updatedAt"), b(String.valueOf(System.currentTimeMillis())));
                } else if (nativeClusterConnection != null) {
                    RedisAdvancedClusterCommands<byte[], byte[]> cmd = nativeClusterConnection.sync();
                    cmd.hset(keyBytes, b("importance"), b(String.valueOf(importance * factor)));
                    cmd.hset(keyBytes, b("updatedAt"), b(String.valueOf(System.currentTimeMillis())));
                }
            } catch (Exception e) {
                log.warn(">>> decay 失败, key={}", k, e);
            }
        }
    }

    @Override
    public void deleteBelow(String userId, double threshold) {
        Set<String> ids = stringRedisTemplate.opsForSet().members(USER_SET_PREFIX + userId);
        if (ids == null || ids.isEmpty()) return;

        for (String id : ids) {
            String k = key(userId, id);
            Map<String, Object> hash = readHashNative(k);
            if (hash.isEmpty() || "1".equals(hash.get("invalid"))) continue;

            double importance = parseDouble(hash.get("importance"), 0.5);
            if (importance < threshold) {
                try {
                    if (nativeConnection != null) {
                        nativeConnection.sync().del(b(k));
                    } else if (nativeClusterConnection != null) {
                        nativeClusterConnection.sync().del(b(k));
                    }
                    stringRedisTemplate.opsForSet().remove(USER_SET_PREFIX + userId, id);
                    stringRedisTemplate.delete(REVERSE_PREFIX + id);
                } catch (Exception e) {
                    log.warn(">>> 删除低分记忆失败, key={}", k, e);
                }
            }
        }
    }

    // ===================== 原生 Hash 读取 =====================

    /**
     * 用原生 Lettuce 读整个 Hash，返回 String -> String（embedding 字段以 Base64 或跳过）
     */
    private Map<String, Object> readHashNative(String key) {
        byte[] keyBytes = b(key);
        Map<String, Object> result = new HashMap<>();
        try {
            Map<byte[], byte[]> raw;
            if (nativeConnection != null) {
                raw = nativeConnection.sync().hgetall(keyBytes);
            } else if (nativeClusterConnection != null) {
                raw = nativeClusterConnection.sync().hgetall(keyBytes);
            } else {
                return result;
            }
            if (raw == null || raw.isEmpty()) return result;

            for (Map.Entry<byte[], byte[]> e : raw.entrySet()) {
                String field = new String(e.getKey(), StandardCharsets.UTF_8);
                // embedding 是二进制，读出来没意义，跳过
                if ("embedding".equals(field)) continue;
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
            java.util.Set<String> activeUsers = new java.util.HashSet<>();
            String pattern = USER_SET_PREFIX + "*";

            ScanOptions scanOptions = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(100)
                    .build();

            // 用 StringRedisTemplate 扫，与写入序列化器保持一致
            stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
                Cursor<byte[]> cursor = connection.keyCommands().scan(scanOptions);
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
}