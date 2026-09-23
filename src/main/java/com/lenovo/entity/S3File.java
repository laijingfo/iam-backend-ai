package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.lenovo.util.AmazonUtil;
import lombok.Data;

/**
 * @TableName s3_file
 */
@TableName(value = "s3_file")
@Data
public class S3File implements Serializable {
    /**
     *
     */

    private Integer id;

    /**
     *
     */
    private String key;

    /**
     *
     */
    private String bucketName;




    public String signedUrl() {
        return AmazonUtil.genSignedUrl(bucketName, key);
    }

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}