package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

import lombok.Data;

/**
 * @TableName code_map
 */
@TableName(value = "code_map")
@Data
public class CodeMap implements Serializable {

    @TableId(type = IdType.AUTO)
    private Integer id;
    /**
     *
     */
    private String type;

    /**
     *
     */
    private String name;

    private String enName;

    /**
     *
     */
    private Integer value;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}