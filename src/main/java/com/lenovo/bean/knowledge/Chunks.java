package com.lenovo.bean.knowledge;

import lombok.Data;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-20 15:58
 * @project iam-backend
 * @description
 */
@Data
public class Chunks {

    /**
     * 文档的 docId
     */
    private String docId;
    /**
     * 关键词模糊检索
     */
    private String keyword;
    /**
     * 当前页
     */
    private long page;
    /**
     * 分页单位
     */
    private long pageSize;
}
