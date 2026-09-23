package com.lenovo.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DenyEditRole {

    /**
     * 角色列表
     * @DenyEditRole(roles = {"admin", "user"})   // 标准写法
     * @DenyEditRole(roles = "guest")             // 单值时可省略大括号
     */
     String[] roles();

    /**
     * 逻辑关系：true表示所有角色都匹配时禁止，false表示任一角色匹配时禁止
     */
    boolean matchAll() default false;


}
