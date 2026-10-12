package com.lenovo.ai.memory;                                                   // 包声明：记忆层

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.lettuce.core.AbstractRedisClient;                                     // Lettuce 抽象客户端
import io.lettuce.core.RedisClient;                                             // Lettuce 单机客户端
import io.lettuce.core.api.StatefulRedisConnection;                             // 单机连接
import io.lettuce.core.api.sync.RedisCommands;                                  // 单机同步命令
import io.lettuce.core.cluster.RedisClusterClient;                              // Lettuce 集群客户端
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;              // 集群连接
import io.lettuce.core.cluster.api.sync.RedisAdvancedClusterCommands;           // 集群同步命令
import io.lettuce.core.codec.ByteArrayCodec;                                    // 字节数组编解码器
import io.lettuce.core.output.NestedMultiOutput;                                // 嵌套多值输出
import io.lettuce.core.output.StatusOutput;                                     // 状态输出
import io.lettuce.core.protocol.CommandArgs;                                    // 命令参数
import io.lettuce.core.protocol.ProtocolKeyword;                                // 协议关键字
import lombok.extern.slf4j.Slf4j;                                               // Lombok：日志
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;                      // Spring 值注入
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory; // Lettuce 连接工厂
import org.springframework.data.redis.core.Cursor;                              // 游标
import org.springframework.data.redis.core.RedisCallback;                       // Redis 回调
import org.springframework.data.redis.core.ScanOptions;                         // SCAN 选项
import org.springframework.data.redis.core.StringRedisTemplate;                 // String Redis 模板
import org.springframework.stereotype.Component;                                // Spring 组件

import jakarta.annotation.PostConstruct;                                       // 初始化注解
import jakarta.annotation.PreDestroy;                                           // 销毁注解
import java.nio.ByteBuffer;                                                     // 字节缓冲
import java.nio.ByteOrder;                                                      // 字节序
import java.nio.charset.StandardCharsets;                                       // 字符集
import java.util.ArrayList;                                                     // 数组列表
import java.util.HashMap;                                                       // HashMap
import java.util.List;                                                          // 列表
import java.util.Map;                                                           // Map
import java.util.Set;                                                           // 集合
import java.util.concurrent.TimeUnit;                                           // 时间单位
import java.util.function.BiConsumer;                                           // 双参消费者

@Slf4j                                                                          // 注入 log 字段
@Component                                                                      // 注册为 Spring Bean
public class RedisMemoryStore implements MemoryStore {                          // Redis 记忆存储实现

    private static final String INDEX_NAME = "idx:ai:memory";                   // RediSearch 索引名
    private static final String KEY_PREFIX = "ai:memory:";                      // 主数据 key 前缀
    private static final String USER_SET_PREFIX = "ai:memory:user:";            // 用户集合 key 前缀
    private static final String REVERSE_PREFIX = "ai:memory:reverse:";          // 反向索引 key 前缀
    private static final long TTL_SECONDS = TimeUnit.DAYS.toSeconds(90);        // TTL：90 天

    @Value("${langchain4j.open-ai.embedding-model.dimension:1536}")                                    // 向量维度（配置）
    private int vectorDim;                                                      // 向量维度字段

    /** 主数据（Hash）用原生 Lettuce，裸字节读写 */                              // 字段注释
    private final LettuceConnectionFactory connectionFactory;                   // Lettuce 连接工厂
    /** 辅助 key（Set / String）统一用 StringRedisTemplate，序列化器为 String */ // 字段注释
    private final StringRedisTemplate stringRedisTemplate;                      // String Redis 模板

    private StatefulRedisConnection<byte[], byte[]> nativeConnection;           // 原生单机连接
    private StatefulRedisClusterConnection<byte[], byte[]> nativeClusterConnection; // 原生集群连接
    private final EmbeddingModel embeddingModel;
    /**                                                                         // Javadoc 开始
     * 构造注入。                                                                // 方法说明
     */                                                                         // Javadoc 结束
    public RedisMemoryStore(LettuceConnectionFactory connectionFactory,         // 构造器
                            StringRedisTemplate stringRedisTemplate, EmbeddingModel embeddingModel) {
        this.connectionFactory = connectionFactory;                             // 赋值
        this.stringRedisTemplate = stringRedisTemplate;                         // 赋值
        this.embeddingModel = embeddingModel;
    }

    /**                                                                         // Javadoc 开始
     * 初始化：建立原生连接 + 创建索引。                                            // 方法说明
     */                                                                         // Javadoc 结束
    @PostConstruct
    public void init() {
        // ✅ 新增：启动时校验维度
        try {
            int actualDim = embeddingModel.dimension();   // LangChain4j 提供
            if (actualDim != vectorDim) {
                throw new IllegalStateException(String.format(
                        "向量维度配置错误：ai.embedding.dimension=%d, 实际 embedding 模型维度=%d, " +
                                "请修改配置后重启", vectorDim, actualDim));
            }
            log.info(">>> 向量维度校验通过: {}", vectorDim);
        } catch (Exception e) {
            log.error(">>> 向量维度校验失败", e);
            throw e;   // 启动失败，避免静默失效
        }
        initNativeConnection();
        initIndex();
    }
    /**                                                                         // Javadoc 开始
     * 建立原生 Lettuce 连接（单机/集群自适应）。                                   // 方法说明
     */                                                                         // Javadoc 结束
    private void initNativeConnection() {                                       // 方法
        try {                                                                   // try
            Object client = connectionFactory.getNativeClient();                //    取原生客户端
            log.info(">>> nativeClient 类型: {}", client == null ? "null" : client.getClass().getName()); // 日志

            if (client instanceof RedisClient rc) {                             //    单机客户端
                nativeConnection = rc.connect(ByteArrayCodec.INSTANCE);         //    建立连接
                log.info(">>> 已建立原生 Lettuce 单机连接");                       //    日志
            } else if (client instanceof RedisClusterClient rcc) {              //    集群客户端
                nativeClusterConnection = rcc.connect(ByteArrayCodec.INSTANCE); //    建立集群连接
                log.info(">>> 已建立原生 Lettuce 集群连接");                       //    日志
            } else if (client instanceof AbstractRedisClient arc) {             //    其他抽象子类
                log.warn(">>> 未知 AbstractRedisClient 子类: {}", arc.getClass().getName()); // 警告
            } else {                                                            //    未识别
                log.warn(">>> 未识别的原生客户端类型: {}", client == null ? "null" : client.getClass().getName()); // 警告
            }
        } catch (Exception e) {                                                 // catch
            log.error(">>> 建立原生 Lettuce 连接失败", e);                        //    日志
        }
    }

    /**                                                                         // Javadoc 开始
     * 创建 RediSearch 索引（若已存在则跳过）。                                     // 方法说明
     */                                                                         // Javadoc 结束
    private void initIndex() {                                                  // 方法
        if (nativeConnection == null && nativeClusterConnection == null) {      // 1. 连接校验
            log.warn(">>> 原生连接未建立，跳过索引创建");                           //    日志
            return;                                                             //    返回
        }
        try {                                                                   // 2. try
            List<byte[]> args = new ArrayList<>();                              //    参数列表
            args.add(b("FT.CREATE"));                                           //    命令
            args.add(b(INDEX_NAME));                                            //    索引名
            args.add(b("ON"));            args.add(b("HASH"));                  //    ON HASH
            args.add(b("PREFIX"));        args.add(b("1")); args.add(b(KEY_PREFIX)); // PREFIX 1 ai:memory:
            args.add(b("SCHEMA"));                                              //    SCHEMA
            args.add(b("userId"));        args.add(b("TAG"));                   //    userId TAG
            args.add(b("type"));          args.add(b("TAG"));                   //    type TAG
            args.add(b("content"));       args.add(b("TEXT"));                  //    content TEXT
            args.add(b("importance"));    args.add(b("NUMERIC"));               //    importance NUMERIC
            args.add(b("createdAt"));     args.add(b("NUMERIC"));               //    createdAt NUMERIC
            args.add(b("updatedAt"));     args.add(b("NUMERIC"));               //    updatedAt NUMERIC
            args.add(b("invalid"));       args.add(b("TAG"));                   //    invalid TAG
            args.add(b("embedding"));     args.add(b("VECTOR"));                //    embedding VECTOR
            args.add(b("HNSW"));          args.add(b("6"));                     //    HNSW 6 参数
            args.add(b("TYPE"));          args.add(b("FLOAT32"));               //    TYPE FLOAT32
            args.add(b("DIM"));           args.add(b(String.valueOf(vectorDim)));//    DIM {vectorDim}
            args.add(b("DISTANCE_METRIC")); args.add(b("COSINE"));              //    DISTANCE_METRIC COSINE

            Object result = dispatch("FT.CREATE", args, OutputType.STATUS);     //    派发命令
            log.info(">>> RediSearch 索引创建成功: {}, result={}", INDEX_NAME, result); // 日志
        } catch (Exception e) {                                                 // 3. 异常
            String msg = e.getMessage() == null ? "" : e.getMessage();          //    取消息
            if (msg.contains("Index already exists")) {                         //    索引已存在
                log.info(">>> RediSearch 索引已存在: {}", INDEX_NAME);            //    日志
            } else {                                                            //    其他错误
                log.warn(">>> RediSearch 索引创建失败: {}", msg, e);              //    日志
            }
        }
    }

    /**                                                                         // Javadoc 开始
     * 销毁：关闭原生连接。                                                        // 方法说明
     */                                                                         // Javadoc 结束
    @PreDestroy                                                                 // 销毁注解
    public void closeNativeConnection() {                                       // 方法
        try { if (nativeConnection != null) nativeConnection.close(); } catch (Exception ignore) {} // 关闭单机
        try { if (nativeClusterConnection != null) nativeClusterConnection.close(); } catch (Exception ignore) {} // 关闭集群
    }

    // ===================== 保存 =====================                            // 分区：保存

    /**                                                                         // Javadoc 开始
     * 保存记忆：主数据（Hash）+ 辅助索引（Set + String）。                          // 方法说明
     */                                                                         // Javadoc 结束
    @Override                                                                   // 实现接口
    public void save(MemoryItem item) {                                         // 保存记忆
        String key = key(item.userId(), item.id());                             // 1. 构造主键
        byte[] keyBytes = b(key);                                               //    转字节
        byte[] embeddingBytes = toFloatBytes(item.embedding());                 // 2. 向量转 float32 字节

        // 1. 主数据：原生 Lettuce 写 Hash                                         // 注释
        try {                                                                   // 3. try
            if (nativeConnection != null) {                                     //    单机连接
                RedisCommands<byte[], byte[]> cmd = nativeConnection.sync();    //    同步命令
                writeFields((k, v) -> cmd.hset(keyBytes, k, v), item, embeddingBytes); // 写字段
                cmd.expire(keyBytes, TTL_SECONDS);                              //    设置 TTL
            } else if (nativeClusterConnection != null) {                       //    集群连接
                RedisAdvancedClusterCommands<byte[], byte[]> cmd = nativeClusterConnection.sync(); // 同步
                writeFields((k, v) -> cmd.hset(keyBytes, k, v), item, embeddingBytes); // 写字段
                cmd.expire(keyBytes, TTL_SECONDS);                              //    设置 TTL
            } else {                                                            //    未初始化
                log.warn(">>> save: 原生连接未初始化");                          //    日志
                return;                                                         //    返回
            }
        } catch (Exception e) {                                                 // 4. 异常
            log.error(">>> 保存记忆主数据失败, key={}", key, e);                  //    日志
            return;                                                             //    返回
        }

        // 2. 辅助索引：StringRedisTemplate，纯字符串                               // 注释
        try {                                                                   // 5. try
            String userSetKey = USER_SET_PREFIX + item.userId();                //    用户集合 key
            String reverseKey = REVERSE_PREFIX + item.id();                     //    反向索引 key

            stringRedisTemplate.opsForSet().add(userSetKey, item.id());         //    加入用户集合
            stringRedisTemplate.expire(userSetKey, TTL_SECONDS, TimeUnit.SECONDS); // 设置 TTL
            stringRedisTemplate.opsForValue().set(reverseKey, item.userId(), TTL_SECONDS, TimeUnit.SECONDS); // 写反向索引
        } catch (Exception e) {                                                 // 6. 异常
            log.error(">>> 保存记忆辅助索引失败, userId={}, id={}", item.userId(), item.id(), e); // 日志
        }
    }

    /**                                                                         // Javadoc 开始
     * 写入 Hash 字段（回调式，兼容单机/集群）。                                     // 方法说明
     */                                                                         // Javadoc 结束
    private void writeFields(BiConsumer<byte[], byte[]> hset, MemoryItem item, byte[] emb) { // 方法
        hset.accept(b("id"), b(item.id()));                                     // id 字段
        hset.accept(b("userId"), b(item.userId()));                             // userId 字段
        hset.accept(b("type"), b(item.type()));                                 // type 字段
        hset.accept(b("content"), b(item.content() == null ? "" : item.content())); // content 字段
        hset.accept(b("importance"), b(String.valueOf(item.importance())));     // importance 字段
        hset.accept(b("createdAt"), b(String.valueOf(item.createdAt())));       // createdAt 字段
        hset.accept(b("updatedAt"), b(String.valueOf(item.updatedAt())));       // updatedAt 字段
        hset.accept(b("invalid"), b(item.invalid() ? "1" : "0"));               // invalid 字段
        if (emb != null && emb.length > 0) {                                    // 向量非空
            hset.accept(b("embedding"), emb);                                   // embedding 字段
        }
    }

    // ===================== 向量检索 =====================                        // 分区：检索

    /**                                                                         // Javadoc 开始
     * KNN 向量召回：按用户过滤 + 无效过滤 + 余弦相似度排序。                         // 方法说明
     */                                                                         // Javadoc 结束
    @Override                                                                   // 实现接口
    public List<MemoryItem> searchByUser(String userId, List<Double> queryEmbedding, int topK) { // KNN 检索
        if (queryEmbedding == null || queryEmbedding.isEmpty()) {               // 1. 向量空
            log.warn(">>> 向量召回跳过：queryEmbedding 为空, userId={}", userId); //    日志
            return List.of();                                                   //    返回空
        }
        if (queryEmbedding.size() != vectorDim) {                               // 2. 维度不符
            log.warn(">>> 向量召回跳过：维度不符, 期望={}, 实际={}", vectorDim, queryEmbedding.size()); // 日志
            return List.of();                                                   //    返回空
        }

        byte[] qBytes = toFloatBytes(queryEmbedding);                           // 3. 向量转字节
        String query = String.format(                                           // 4. 构造查询
                "(@userId:{%s} @invalid:{0})=>[KNN %d @embedding $vec AS score]", // TAG 过滤 + KNN
                escapeTag(userId), topK                                         //    用户 ID 转义 + topK
        );

        try {                                                                   // 5. try
            List<byte[]> args = new ArrayList<>();                              //    参数列表
            args.add(b("FT.SEARCH"));                                           //    命令
            args.add(b(INDEX_NAME));                                            //    索引名
            args.add(b(query));                                                 //    查询
            args.add(b("PARAMS"));   args.add(b("2"));                          //    PARAMS 2
            args.add(b("vec"));      args.add(qBytes);                          //    参数 vec
            args.add(b("SORTBY"));   args.add(b("score"));                      //    排序
            args.add(b("DIALECT"));  args.add(b("2"));                          //    DIALECT 2
            args.add(b("LIMIT"));    args.add(b("0")); args.add(b(String.valueOf(topK))); // LIMIT

            Object raw = dispatch("FT.SEARCH", args, OutputType.NESTED);        //    派发命令
            return parseSearchResult(raw);                                      //    解析结果
        } catch (Exception e) {                                                 // 6. 异常
            log.error(">>> 向量召回失败, userId={}", userId, e);                  //    日志
            return List.of();                                                   //    返回空
        }
    }

    // ===================== 其他实现 =====================                         // 分区：其他

    /**                                                                         // Javadoc 开始
     * 按内容子串模糊查询（用于去重）。                                             // 方法说明
     */                                                                         // Javadoc 结束
    @Override                                                                   // 实现接口
    public List<MemoryItem> findByUserAndContentLike(String userId, String keyword) { // 方法
        Set<String> ids = stringRedisTemplate.opsForSet().members(USER_SET_PREFIX + userId); // 取用户记忆 ID 集合
        if (ids == null || ids.isEmpty()) return List.of();                     // 空集合返回

        List<MemoryItem> result = new ArrayList<>();                            // 结果列表
        for (String id : ids) {                                                 // 遍历
            Map<String, Object> hash = readHashNative(key(userId, id));         //    读 Hash
            if (hash.isEmpty() || "1".equals(hash.get("invalid"))) continue;    //    跳过无效
            if (String.valueOf(hash.get("content")).contains(keyword)) {        //    内容匹配
                result.add(toItem(hash));                                       //    加入结果
            }
        }
        return result;                                                          // 返回
    }

    /**                                                                         // Javadoc 开始
     * 失效记忆：置 invalid=1 + 更新 updatedAt。                                    // 方法说明
     */                                                                         // Javadoc 结束
    @Override                                                                   // 实现接口
    public void invalidate(String memoryId) {                                   // 方法
        String userId = stringRedisTemplate.opsForValue().get(REVERSE_PREFIX + memoryId); // 反向查 userId
        if (userId == null) return;                                             // 找不到返回

        String k = key(userId, memoryId);                                       // 构造主键
        byte[] keyBytes = b(k);                                                 // 转字节
        try {                                                                   // try
            if (nativeConnection != null) {                                     //    单机
                RedisCommands<byte[], byte[]> cmd = nativeConnection.sync();    //    同步命令
                cmd.hset(keyBytes, b("invalid"), b("1"));                       //    置无效
                cmd.hset(keyBytes, b("updatedAt"), b(String.valueOf(System.currentTimeMillis()))); // 更新
            } else if (nativeClusterConnection != null) {                       //    集群
                RedisAdvancedClusterCommands<byte[], byte[]> cmd = nativeClusterConnection.sync(); // 同步
                cmd.hset(keyBytes, b("invalid"), b("1"));                       //    置无效
                cmd.hset(keyBytes, b("updatedAt"), b(String.valueOf(System.currentTimeMillis()))); // 更新
            }
        } catch (Exception e) {                                                 // 异常
            log.error(">>> 失效记忆失败, memoryId={}", memoryId, e);              //    日志
        }
    }

    /**                                                                         // Javadoc 开始
     * 衰减所有记忆的重要性：importance *= factor。                                 // 方法说明
     */                                                                         // Javadoc 结束
    @Override                                                                   // 实现接口
    public void decayAll(String userId, double factor) {                        // 方法
        Set<String> ids = stringRedisTemplate.opsForSet().members(USER_SET_PREFIX + userId); // 取用户记忆 ID
        if (ids == null || ids.isEmpty()) return;                               // 空返回

        for (String id : ids) {                                                 // 遍历
            String k = key(userId, id);                                         //    构造主键
            byte[] keyBytes = b(k);                                             //    转字节
            Map<String, Object> hash = readHashNative(k);                       //    读 Hash
            if (hash.isEmpty() || "1".equals(hash.get("invalid"))) continue;    //    跳过无效

            double importance = parseDouble(hash.get("importance"), 0.5);       //    取重要性
            try {                                                               //    try
                if (nativeConnection != null) {                                 //        单机
                    RedisCommands<byte[], byte[]> cmd = nativeConnection.sync();//        同步
                    cmd.hset(keyBytes, b("importance"), b(String.valueOf(importance * factor))); // 衰减
                    cmd.hset(keyBytes, b("updatedAt"), b(String.valueOf(System.currentTimeMillis()))); // 更新
                } else if (nativeClusterConnection != null) {                   //        集群
                    RedisAdvancedClusterCommands<byte[], byte[]> cmd = nativeClusterConnection.sync(); // 同步
                    cmd.hset(keyBytes, b("importance"), b(String.valueOf(importance * factor))); // 衰减
                    cmd.hset(keyBytes, b("updatedAt"), b(String.valueOf(System.currentTimeMillis()))); // 更新
                }
            } catch (Exception e) {                                             //    异常
                log.warn(">>> decay 失败, key={}", k, e);                        //    日志
            }
        }
    }

    /**                                                                         // Javadoc 开始
     * 删除重要性低于阈值的记忆。                                                   // 方法说明
     */                                                                         // Javadoc 结束
    @Override                                                                   // 实现接口
    public void deleteBelow(String userId, double threshold) {                  // 方法
        Set<String> ids = stringRedisTemplate.opsForSet().members(USER_SET_PREFIX + userId); // 取用户记忆 ID
        if (ids == null || ids.isEmpty()) return;                               // 空返回

        for (String id : ids) {                                                 // 遍历
            String k = key(userId, id);                                         //    构造主键
            Map<String, Object> hash = readHashNative(k);                       //    读 Hash
            if (hash.isEmpty() || "1".equals(hash.get("invalid"))) continue;    //    跳过无效

            double importance = parseDouble(hash.get("importance"), 0.5);       //    取重要性
            if (importance < threshold) {                                       //    低于阈值
                try {                                                           //    try
                    if (nativeConnection != null) {                             //        单机
                        nativeConnection.sync().del(b(k));                      //        删除主数据
                    } else if (nativeClusterConnection != null) {               //        集群
                        nativeClusterConnection.sync().del(b(k));               //        删除主数据
                    }
                    stringRedisTemplate.opsForSet().remove(USER_SET_PREFIX + userId, id); // 移除集合
                    stringRedisTemplate.delete(REVERSE_PREFIX + id);            //    删除反向索引
                } catch (Exception e) {                                         //    异常
                    log.warn(">>> 删除低分记忆失败, key={}", k, e);               //    日志
                }
            }
        }
    }

    // ===================== 原生 Hash 读取 =====================                    // 分区：Hash 读取

    /**                                                                         // Javadoc 开始
     * 用原生 Lettuce 读整个 Hash，返回 String -> String（embedding 字段跳过）。      // 方法说明
     */                                                                         // Javadoc 结束
    private Map<String, Object> readHashNative(String key) {                    // 方法
        byte[] keyBytes = b(key);                                               // 转字节
        Map<String, Object> result = new HashMap<>();                           // 结果 Map
        try {                                                                   // try
            Map<byte[], byte[]> raw;                                            //    原始 Map
            if (nativeConnection != null) {                                     //    单机
                raw = nativeConnection.sync().hgetall(keyBytes);                //    HGETALL
            } else if (nativeClusterConnection != null) {                       //    集群
                raw = nativeClusterConnection.sync().hgetall(keyBytes);         //    HGETALL
            } else {                                                            //    未初始化
                return result;                                                  //    返回空
            }
            if (raw == null || raw.isEmpty()) return result;                    //    空返回

            for (Map.Entry<byte[], byte[]> e : raw.entrySet()) {                //    遍历
                String field = new String(e.getKey(), StandardCharsets.UTF_8);  //    字段名
                // embedding 是二进制，读出来没意义，跳过                             //     注释
                if ("embedding".equals(field)) continue;                        //    跳过 embedding
                result.put(field, new String(e.getValue(), StandardCharsets.UTF_8)); // 其他字段转 String
            }
        } catch (Exception e) {                                                 // 异常
            log.warn(">>> 读取 Hash 失败, key={}", key, e);                      //    日志
        }
        return result;                                                          // 返回
    }

    // ===================== 派发 =====================                             // 分区：命令派发

    /** 输出类型枚举。 */                                                          // 注释
    private enum OutputType { STATUS, NESTED }                                  // STATUS=简单状态，NESTED=嵌套结果

    /**                                                                         // Javadoc 开始
     * 派发原生 Redis 命令（兼容单机/集群）。                                        // 方法说明
     */                                                                         // Javadoc 结束
    private Object dispatch(String command, List<byte[]> args, OutputType outputType) { // 方法
        CommandArgs<byte[], byte[]> cmdArgs = new CommandArgs<>(ByteArrayCodec.INSTANCE); // 命令参数
        for (int i = 1; i < args.size(); i++) cmdArgs.add(args.get(i));         // 从 args[1] 开始添加（跳过命令名）
        ProtocolKeyword keyword = command::getBytes;                            // 命令关键字

        if (nativeConnection != null) {                                         // 单机
            RedisCommands<byte[], byte[]> commands = nativeConnection.sync();   // 同步命令
            return outputType == OutputType.STATUS                              // 按类型选择 Output
                    ? commands.dispatch(keyword, new StatusOutput<>(ByteArrayCodec.INSTANCE), cmdArgs)
                    : commands.dispatch(keyword, new NestedMultiOutput<>(ByteArrayCodec.INSTANCE), cmdArgs);
        }
        if (nativeClusterConnection != null) {                                  // 集群
            RedisAdvancedClusterCommands<byte[], byte[]> commands = nativeClusterConnection.sync(); // 同步
            return outputType == OutputType.STATUS                              // 按类型选择 Output
                    ? commands.dispatch(keyword, new StatusOutput<>(ByteArrayCodec.INSTANCE), cmdArgs)
                    : commands.dispatch(keyword, new NestedMultiOutput<>(ByteArrayCodec.INSTANCE), cmdArgs);
        }
        throw new IllegalStateException("原生 Lettuce 连接未初始化");              // 未初始化抛异常
    }

    // ===================== 工具 =====================                             // 分区：工具方法

    /** 字符串转字节。 */                                                          // 注释
    private byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }   // 方法

    /**                                                                         // Javadoc 开始
     * 构造主键：ai:memory:{userId}:{memoryId}。                                   // 方法说明
     */                                                                         // Javadoc 结束
    private String key(String userId, String memoryId) {                        // 方法
        return KEY_PREFIX + userId + ":" + memoryId;                            // 拼接
    }

    /**                                                                         // Javadoc 开始
     * 转义 RediSearch TAG 特殊字符。                                              // 方法说明
     */                                                                         // Javadoc 结束
    private String escapeTag(String s) {                                        // 方法
        return s.replaceAll("([,\\.<>\\-\\[\\]{}()\"'`~!@#$%^&*+=|;:/?\\\\ ])", "\\\\$1"); // 正则转义
    }

    /**                                                                         // Javadoc 开始
     * 将 List<Double> 转 float32 字节（小端序）。                                 // 方法说明
     */                                                                         // Javadoc 结束
    private byte[] toFloatBytes(List<Double> vec) {                             // 方法
        if (vec == null || vec.isEmpty()) return new byte[0];                   // 空返回
        ByteBuffer buf = ByteBuffer.allocate(vec.size() * 4).order(ByteOrder.LITTLE_ENDIAN); // 分配缓冲
        for (Double d : vec) buf.putFloat(d.floatValue());                      // 逐个写入
        return buf.array();                                                     // 返回字节
    }

    /**                                                                         // Javadoc 开始
     * Hash Map 转 MemoryItem。                                                  // 方法说明
     */                                                                         // Javadoc 结束
    private MemoryItem toItem(Map<?, ?> hash) {                                 // 方法
        return new MemoryItem(                                                  // 构造
                String.valueOf(hash.get("id")),                                 //    id
                String.valueOf(hash.get("userId")),                             //    userId
                String.valueOf(hash.get("type")),                               //    type
                String.valueOf(hash.get("content")),                            //    content
                List.of(),                                                      //    embedding 空（Hash 未存）
                parseDouble(hash.get("importance"), 0.5),                       //    importance
                parseLong(hash.get("createdAt"), 0L),                           //    createdAt
                parseLong(hash.get("updatedAt"), 0L),                           //    updatedAt
                "1".equals(String.valueOf(hash.get("invalid")))                 //    invalid
        );
    }

    /** 安全解析 double。 */                                                       // 注释
    private double parseDouble(Object o, double def) {                          // 方法
        if (o == null) return def;                                              // 空返回默认
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return def; } // 解析或默认
    }

    /** 安全解析 long。 */                                                         // 注释
    private long parseLong(Object o, long def) {                                // 方法
        if (o == null) return def;                                              // 空返回默认
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return def; } // 解析或默认
    }

    /**                                                                         // Javadoc 开始
     * 解析 FT.SEARCH 结果（嵌套 List 结构）。                                     // 方法说明
     */                                                                         // Javadoc 结束
    private List<MemoryItem> parseSearchResult(Object raw) {                    // 方法
        List<MemoryItem> result = new ArrayList<>();                            // 结果
        if (!(raw instanceof List<?> list) || list.isEmpty()) return result;    // 非 List 或空返回

        int i = 1;                                                              // 从 1 开始（跳过分组数量）
        while (i + 1 < list.size()) {                                           // 遍历
            Object fieldsObj = list.get(i + 1);                                 //    取字段列表
            if (fieldsObj instanceof List<?> fields) {                          //    是 List
                Map<String, Object> map = new HashMap<>();                      //    字段 Map
                for (int j = 0; j + 1 < fields.size(); j += 2) {                //    遍历字段
                    map.put(String.valueOf(fields.get(j)), fields.get(j + 1));  //    键值对
                }
                if (map.get("id") != null) result.add(toItem(map));             //    id 存在则加入
            }
            i += 2;                                                             //    跳到下一组
        }
        return result;                                                          // 返回
    }

    /**                                                                         // Javadoc 开始
     * SCAN 遍历所有活跃用户（有记忆记录的用户）。                                   // 方法说明
     */                                                                         // Javadoc 结束
    @Override                                                                   // 实现接口
    public Set<String> getAllActiveUsers() {                                    // 方法
        try {                                                                   // try
            java.util.Set<String> activeUsers = new java.util.HashSet<>();      //    结果集
            String pattern = USER_SET_PREFIX + "*";                             //    匹配模式

            ScanOptions scanOptions = ScanOptions.scanOptions()                 //    SCAN 选项
                    .match(pattern)                                             //    匹配
                    .count(100)                                                 //    每批 100
                    .build();                                                   //    构建

            // 用 StringRedisTemplate 扫，与写入序列化器保持一致                     //     注释
            stringRedisTemplate.execute((RedisCallback<Void>) connection -> {   //    执行 SCAN
                Cursor<byte[]> cursor = connection.keyCommands().scan(scanOptions); // 游标
                try {                                                           //    try
                    while (cursor.hasNext()) {                                  //        遍历
                        byte[] keyBytes = cursor.next();                        //        取 key
                        if (keyBytes != null) {                                 //        非空
                            String key = new String(keyBytes, StandardCharsets.UTF_8); // 转 String
                            if (key.startsWith(USER_SET_PREFIX)) {              //        前缀匹配
                                String userId = key.substring(USER_SET_PREFIX.length()); // 截取 userId
                                if (!userId.isBlank()) {                        //        非空
                                    activeUsers.add(userId);                    //        加入结果
                                }
                            }
                        }
                    }
                } finally {                                                     //    finally
                    if (cursor != null) cursor.close();                         //        关闭游标
                }
                return null;                                                    //    返回
            });

            log.info(">>> 获取活跃用户成功，共 {} 个", activeUsers.size());        //    日志
            return activeUsers;                                                 //    返回
        } catch (Exception e) {                                                 // 异常
            log.warn(">>> 获取活跃用户失败", e);                                   //    日志
            throw e;                                                            //    抛出（调用方决定降级）
        }
    }
    
    /**
     * 查找用户的所有记忆（调试用）
     */
    @Override
    public List<MemoryItem> findAllByUser(String userId) {
        List<MemoryItem> result = new ArrayList<>();
        
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            String pattern = KEY_PREFIX + userId + ":*";
            Cursor<byte[]> cursor = connection.keyCommands()
                .scan(ScanOptions.scanOptions().match(pattern).build());
            
            try {
                while (cursor.hasNext()) {
                    byte[] keyBytes = cursor.next();
                    if (keyBytes != null) {
                        String key = new String(keyBytes, StandardCharsets.UTF_8);
                        String memoryId = key.substring((KEY_PREFIX + userId + ":").length());
                        
                        // 从 Hash 读取记忆内容
                        Map<byte[], byte[]> hash = connection.hashCommands().hGetAll(key.getBytes());
                        if (!hash.isEmpty()) {
                            result.add(toItem(convertMap(hash)));
                        }
                    }
                }
            } finally {
                if (cursor != null) cursor.close();
            }
            return null;
        });
        
        log.info(">>> 用户 {} 共有 {} 条记忆", userId, result.size());
        return result;
    }
    
    private Map<String, String> convertMap(Map<byte[], byte[]> bytesMap) {
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<byte[], byte[]> entry : bytesMap.entrySet()) {
            map.put(new String(entry.getKey(), StandardCharsets.UTF_8),
                    new String(entry.getValue(), StandardCharsets.UTF_8));
        }
        return map;
    }
}