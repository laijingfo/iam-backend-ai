package com.lenovo.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.lenovo.security.utils.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Date;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // 插入时填充创建时间和创建人
        this.strictInsertFill(metaObject, "createTime", LocalDateTime::now, LocalDateTime.class);
        this.strictInsertFill(metaObject, "createTimestamp", OffsetDateTime::now, OffsetDateTime.class);

        // 插入时同时填充更新时间和更新人
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
        this.strictInsertFill(metaObject, "updateTimestamp", OffsetDateTime::now, OffsetDateTime.class);

        try {
            this.strictInsertFill(metaObject, "createBy", SecurityUtils::getCurrentUsername, String.class);
            this.strictInsertFill(metaObject, "updateBy", SecurityUtils::getCurrentUsername, String.class);
            this.strictInsertFill(metaObject, "lastModifier", SecurityUtils::getCurrentUsername, String.class);

        } catch (Exception e) {
            this.strictInsertFill(metaObject, "createBy", String.class, "SYSTEM");
            this.strictInsertFill(metaObject, "updateBy", String.class, "SYSTEM");
            this.strictInsertFill(metaObject, "lastModifier", String.class, "SYSTEM");
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 更新时只填充更新时间和更新人
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
        this.strictUpdateFill(metaObject, "updateTimestamp", OffsetDateTime::now, OffsetDateTime.class);
        try {
            this.strictUpdateFill(metaObject, "updateBy", SecurityUtils::getCurrentUsername, String.class);
            this.strictUpdateFill(metaObject, "lastModifier", SecurityUtils::getCurrentUsername, String.class);
        } catch (Exception e) {
            this.strictUpdateFill(metaObject, "updateBy", String.class, "SYSTEM");
            this.strictUpdateFill(metaObject, "lastModifier", String.class, "SYSTEM");
        }
    }
}