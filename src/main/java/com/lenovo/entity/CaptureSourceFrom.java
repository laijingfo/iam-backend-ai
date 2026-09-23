package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 
 * @TableName capture_source_from
 */
@TableName(value ="capture_source_from")
@Data
public class CaptureSourceFrom implements Serializable {
    /**
     * key值
     */
    private String sourceKey;

    /**
     * source展示的title
     */
    private String sourceTitle;

    /**
     * 层级
     */
    private Integer level;

    /**
     * 父级id
     */
    private Integer parentId;

    /**
     * 是否展示 默认true
     */
    private Boolean show;

    /**
     * source描述
     */
    private String description;

    /**
     * 
     */
    private Integer id;

    /**
     * 排序顺序
     */
    private Integer rank;

    /**
     * 
     */
    private String url;

    /**
     * 
     */
    private String actions;

    /**
     * 
     */
    private String captureSelector;

    /**
     * 
     */
    private Integer waitTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}