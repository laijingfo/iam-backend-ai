package com.lenovo.bean.knowledge;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-20 13:56
 * @project iam-backend
 * @description
 */
@Data
public class KnowledgeUpload {
        /**
         * 回调通知接口
         */
        private String callbackUrl;
        /**
         * 分片大小    已弃用
         */
        private Double chunkSize;
        /**
         * chunk类型：auto\semantic\custom   semantic\custom已弃用，使用auto
         */
        @NotBlank(message = "chunk类型不能为空")
        private String chunkType;
        /**
         * 要使用的 embedding 模型：bge-m3\m3e-base\bge-large-en-v1.5\bge-large-zh-v1.5
         */
        @NotBlank(message = "embedding模型不能为空")
        private String embeddingModel;
        /**
         * 代表是否对metadata做embedding，非必填，可填0或者1，默认是0
         */
        private String embedMetadata;
        /**
         * 要上传的文档
         */
        @NotBlank(message = "要上传的文档不能为空")
        private String file;
        /**
         * 上传文件类型
         */
        @NotBlank(message = "上传文件类型不能为空")
        private String fileType;
        /**
         * pdf是否由PPT转换生成：0=不是；1=是
         */
        private Long isPPTDerived;
        /**
         * 是否是QA场景：0=不是QA场景;1=是QA场景
         */
        private Long isQA;
        /**
         * 知识库ID
         */
        @NotBlank(message = "知识库ID不能为空")
        private Long knowledgeBaseId;

        private String metadata;
        /**
         * 分片重叠大小      已弃用
         */
        private Double overlapSize;
        /**
         * 文件夹的docId
         */
        private String parentDocId;
        /**
         * 文档原始路径：可传可不传
         */
        private String path;
        /**
         * 项目ID
         */
        @NotBlank(message = "项目ID不能为空")
        private long projectId;
        /**
         * 分片切割符号：空两行\换行\中文句号\英文句号\中文叹号\英文叹号\中文问号\英文问号
         */
        private String shardingFlag;
        /**
         * 源系统
         */
        private String sourceSystem;
        /**
         * 文档需要打的 tag
         */
        private String tag;

}
