package com.lenovo.config;
import java.lang.annotation.*;
/**
 * @author wangFenglong
 * @data 2025/11/12
 * 自定义操作日志注解（标记控制器方法的操作描述,用于记录操作日志到数据库）
 **/
@Target({ElementType.METHOD}) // 仅作用于方法
@Retention(RetentionPolicy.RUNTIME) // 运行时可获取
@Documented
public @interface  LogOperation
{
    String value() default ""; // 操作描述（如：切换系统、新增用户）

    /**
     * 操作模块（如：用户管理、系统配置）
     */
    String module() default "";

    /**
     * 操作类型（如：新增、修改、删除、查询）
     */
    OperationType type() default OperationType.OTHER;

    /**
     * 是否忽略日志记录（默认不忽略）
     */
    boolean ignore() default false;

    /**
     * 操作类型枚举（覆盖常见业务场景）
     */
    enum OperationType
    {
        ADD("新增"), UPDATE("修改"), DELETE("删除"), QUERY("查询"),
        IMPORT("导入"), EXPORT("导出"), LOGIN("登录"), LOGOUT("登出"),
        UPLOAD("上传"), DOWNLOAD("下载"), SYNC("同步"), OTHER("其他");

        private final String desc;

        OperationType(String desc)
        {
            this.desc = desc;
        }

        public String getDesc()
        {
            return desc;
        }
    }
}
