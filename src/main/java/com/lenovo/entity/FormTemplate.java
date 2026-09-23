package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.Data;

/**
 * 
 * @TableName form_template
 */
@TableName(value ="form_template")
@Data
public class FormTemplate implements Serializable {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 
     */
    private String templateName;

    /**
     * 
     */
    private String formTemplate;

    /**
     * 
     */
    private Integer workspaceId;

    /**
     * 
     */
    private String creator;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}