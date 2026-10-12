package com.lenovo.ai.memory;                                                   // 包声明：记忆层

import com.lenovo.ai.llm.Message;                                               // 统一消息模型
import lombok.RequiredArgsConstructor;                                          // Lombok：构造注入
import lombok.extern.slf4j.Slf4j;                                               // Lombok：日志
import org.springframework.scheduling.annotation.Async;                         // Spring 异步注解
import org.springframework.stereotype.Component;                                // Spring 组件

import java.util.List;                                                          // 列表
import java.util.Map;                                                           // Map
import java.util.stream.Collectors;                                             // 流收集器

/**                                                                             // Javadoc 开始
 * 长期记忆抽取工具。                                                             // 类说明
 * <p>                                                                          // 段落
 * 修复：改用 SummaryUtil#extractLongTermMemoriesAsList，                          // 修复说明
 * 不再对字符串做 JSON 解析；单条失败不影响整批。                                   // 隔离说明
 *                                                                              // 空行
 * @author laifo                                                                // 作者
 * @date 2026-09-30                                                             // 日期
 */                                                                             // Javadoc 结束
@Slf4j                                                                          // 注入 log 字段
@Component                                                                      // 注册为 Spring Bean
@RequiredArgsConstructor                                                        // 为 final 字段生成构造器
public class MemoryExtractor {                                                  // 记忆抽取器

    private final MemoryStore memoryStore;                                      // 记忆存储（写入目标）
    private final EmbeddingClient embeddingClient;                              // 向量化客户端
    private final SummaryUtil summaryUtil;                                      // 摘要/抽取工具（LLM 调用）

    /**                                                                         // Javadoc 开始
     * 异步从一轮对话中抽取长期记忆。                                              // 方法说明
     *                                                                          // 空行
     * @param userId 用户 ID                                                     // 参数说明
     * @param turn   本轮对话（user + assistant）                                 // 参数说明
     */                                                                         // Javadoc 结束
    @Async("memoryExecutor")                                                    // 提交到 memoryExecutor 线程池异步执行
    public void extract(String userId, List<Message> turn) {                    // 抽取入口
        try {                                                                   // 主流程 try，异常兜底
            if (turn == null || turn.isEmpty()) {                               // 1. 空对话校验
                log.warn(">>> 记忆抽取跳过：对话为空，userId={}", userId);      //    日志
                return;                                                         //    直接返回
            }
            String dialog = turn.stream()                                       // 2. 拼接本轮对话文本
                    .map(m -> m.role() + ": " + m.content())                    //    格式："ROLE: content"
                    .collect(Collectors.joining("\n"));                         //    换行拼接
            
            log.info(">>> 开始抽取记忆：userId={}, 对话长度={} 字符", userId, dialog.length()); // 调试日志

            // 直接拿 List，SummaryUtil 已保证是合法结构                           // 3. 调用 LLM 抽取（返回结构化列表）
            List<Map<String, Object>> items = summaryUtil.extractLongTermMemoriesAsList(dialog);
            
            log.info(">>> 记忆抽取完成：userId={}, 抽取到 {} 条记忆", userId, items.size()); // 调试日志 // 抽取
            if (items == null || items.isEmpty()) {                             // 4. 无值得记住的信息
                log.info("[MemoryExtractor] 未检测到值得记住的信息");            //    仅 debug 日志
                return;                                                         //    直接返回
            }

            for (Map<String, Object> it : items) {                              // 5. 逐条保存
                saveOne(userId, it);                                            //    单条保存（内部 try-catch 隔离）
            }
        } catch (Exception e) {                                                 // 6. 整体异常兜底
            log.warn("长期记忆抽取失败：{}", e.getMessage(), e);                   //    记录警告（异步方法不抛异常）
        }
    }

    /**                                                                         // Javadoc 开始
     * 保存单条记忆：字段校验 → 去重 → 向量化 → 保存。                              // 方法说明
     * <p>                                                                      // 段落
     * 单条失败不影响其他条目。                                                     // 隔离说明
     *                                                                          // 空行
     * @param userId 用户 ID                                                     // 参数说明
     * @param it     单条记忆（来自 LLM 抽取结果）                                  // 参数说明
     */                                                                         // Javadoc 结束
    private void saveOne(String userId, Map<String, Object> it) {               // 单条保存
        try {                                                                   // 1. 单条 try，异常不扩散
            Object typeObj = it.get("type");                                    //    取 type 字段
            Object contentObj = it.get("content");                              //    取 content 字段
            if (typeObj == null || contentObj == null) {                        // 2. 必填字段校验
                log.warn(">>> 记忆字段缺失，跳过：item={}", it);                   //    日志
                return;                                                         //    跳过该条
            }
            String type = String.valueOf(typeObj);                              // 3. type 转字符串
            String content = String.valueOf(contentObj);                        //    content 转字符串
            if (content.isBlank()) {                                            // 4. content 非空校验
                return;                                                         //    跳过该条
            }
            double importance = 0.5;                                            // 5. 默认重要性 0.5
            Object imp = it.get("importance");                                  //    取 importance
            if (imp instanceof Number) {                                        //    类型判断
                importance = ((Number) imp).doubleValue();                      //    取值
            }

            List<MemoryItem> existing = memoryStore.findByUserAndContentLike(userId, content); // 6. 去重查询
            if (existing != null && !existing.isEmpty()) {                      //    存在相同内容
                memoryStore.invalidate(existing.get(0).id());                   //    失效旧记忆（仅第一条，见待完善点）
            }

            // ===== 降级：embedding 失败则跳过这条记忆的保存 =====                 // 7. 向量化（带降级）
            List<Double> embedding;                                             //    向量容器
            try {                                                               //    内层 try
                embedding = embeddingClient.embed(content);                     //    向量化
            } catch (Exception e) {                                             //    向量化失败
                log.warn(">>> 记忆 embedding 失败，跳过保存：content={}, err={}", //    日志
                        content, e.toString());
                return;                                                         //    跳过该条（降级）
            }
            if (embedding == null || embedding.isEmpty()) {                     // 8. 向量空校验
                log.warn(">>> 记忆 embedding 为空，跳过保存：content={}", content); //    日志
                return;                                                         //    跳过该条
            }

            memoryStore.save(MemoryItem.of(userId, type, content, embedding, importance)); // 9. 保存
            log.info(">>> 成功保存记忆：type={}, content={}",                      // 10. 成功日志
                    type, content.substring(0, Math.min(50, content.length()))); //    截断 50 字
        } catch (Exception e) {                                                 // 11. 单条异常兜底
            // 单条失败不影响其它条目                                             //     注释
            log.warn(">>> 保存单条记忆失败：item={}, err={}", it, e.toString());   //     日志
        }
    }
}