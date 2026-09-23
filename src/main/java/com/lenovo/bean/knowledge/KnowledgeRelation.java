package com.lenovo.bean.knowledge;

import lombok.Data;
import java.util.List;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-17 09:01
 * @project iam-backend
 * @description
 */
@Data
public class KnowledgeRelation {

    private Long knowledgeBaseId;   //要检索的知识库

    private String filter;   //过滤条件 如果要是文档过滤：{"docId":["aaaaa"]}

    private List<String> tags;   //根据 Tag 进行检索过滤

    private List<String> folders;   //进行过滤的文件夹

    private String embedding;   //上传是使用的 embedding model
            
    private String filterLogic;   //过滤形式

}
