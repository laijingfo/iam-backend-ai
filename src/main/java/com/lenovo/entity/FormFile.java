package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

import lombok.Data;

/**
 * @TableName form_file
 */
@TableName(value = "form_file")
@Data
public class FormFile implements Serializable {
    /**
     *
     */

    private Integer id;

    /**
     *
     */
    private Integer fileId;

    /**
     *
     */
    private String fileType;

    /**
     *
     */
    private String url;

    /**
     *
     */
    private Long expires;

    private String fileName;

    @TableField(exist = false)
    private S3File s3File;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}