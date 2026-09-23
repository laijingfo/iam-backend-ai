package com.lenovo.bean.knowledge;

import lombok.Data;

import java.util.List;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-17 09:00
 * @project iam-backend
 * @description 知识库实体类
 */
@Data
public class KnowledgeBase {

    private Integer projectId;   //指定项目ID

    private List<KnowledgeRelation> relation;

    private String query;       //检索内容

    private String indexMode;   //检索方式：vector/keyword

    private Integer similarityTopK;     //前几

    private Integer score;        //分数过滤

    private String contextCompletion;   //是否补全：上下文补全  all，0， 1， 2，

    private String filenameCompletion;   //是否文件名补全：0=不补全；1=补全

    private Integer websearchSize;   //default 3 外搜/内搜网页数量

    private Integer internalSearch;   //0/1 default 0，知识内搜插件，只有当websearchMode为0时生效

    private Integer websearchMode;   //default 0 - 不启用 1：融合检索；2：外网优先；3：内网优先

    private String webUsername;     //websearch用户名（yongjie那获取的appKey（用户名）） - websearchMode不为0时必填

    private String webApikey;     //websearch API Key（通过aes加密后的baidu/bocha的api Key） - websearchMode不为0时必填

    private String webEngine;     //搜索引擎（bocha_web，baidu_web等)

    private String sessionId;     //消息推送使用的

    private String traceId;     //消息推送使用的

    private String spanId;     //消息推送使用的

    private String chatbotName;     //消息推送使用的
            
    private Integer rerank;     //是否开启rerank，默认100，也可以不启用就是传0，rerank不是0的时候需要大于topk

    private String agent;     //agent name
            
    private List<String> glossaryIds;     //选择的词库ID

    private Boolean returnSummary;     //true/false，标记metadata中是否返回 summary 信息

    private Boolean retrievalResultCache;     //是否启用整次检索结果缓存。命中后跳过 embedding、Milvus、rerank、context/card 后处理

    private Integer retrievalResultCacheTtl;     //结果缓存有效期，单位秒。默认 15 分钟。

    private String retrievalResultCacheMode;     //缓存命中模式。 exact、query_similarity。

    private Double retrievalResultCacheQuerySimilarityThreshold;     //0.95 仅 query_similarity 模式生效。其他检索参数一致时，query 字符串相似度达到阈值才复用缓存。

    private Boolean kmResourceForceRefresh;     //true/false，是否强制刷新知识库资源

    private Boolean useWorkspaceRetrievalConfig;     //true/false，是否使用项目知识库检索配置

    private Long knowledgeBaseId; //指定知识库ID

}
