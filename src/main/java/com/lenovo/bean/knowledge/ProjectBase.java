package com.lenovo.bean.knowledge;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-20 11:00
 * @project iam-backend
 * @description
 */
@Data
public class ProjectBase {

    private String keyword; //模糊检索

    private Integer page; //当前页

    private Integer rows; //每页数量

    @NotBlank(message = "itCode不能为空")
    private String itCode; //项目编码

    private String tab; //workspace/ontology

    private Integer projectId;   //指定项目ID

    private Integer id;

    private String name;

    private String description;

    private String bucket;

    private String createdBy;

    private String llmStatus;

}
