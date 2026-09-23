package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lenovo.security.utils.SecurityUtils;
import lombok.Data;

import java.time.LocalDateTime;


@Data
@TableName("sync_uar_ignore_rule")
public class SyncUarIgnoreRule {
    @TableId(type = IdType.AUTO, value = "id")
    private Long ruleId;
    private String cmdbId;
    private String systemRole;
    private String updateBy;
    private LocalDateTime updateTime;

    public SyncUarIgnoreRule(String cmdbId, String systemRole) {
        this.cmdbId = cmdbId;
        this.systemRole = systemRole;
        this.updateBy = SecurityUtils.getCurrentUserId();
    }
}
