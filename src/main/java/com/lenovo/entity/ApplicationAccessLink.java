package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName(value = "its_application_access_link")
public class ApplicationAccessLink implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String cmdbId;

    @TableField(exist = false)
    private String appName;

    private String accessLink;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}
