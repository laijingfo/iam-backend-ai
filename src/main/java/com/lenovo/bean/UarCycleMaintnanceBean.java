package com.lenovo.bean;

import lombok.Data;

import java.util.Map;

/**
 * @Description TODO (批量上线,批量下线,批量开启周期,批量关闭周期)发送邮件服务 发送参数类
 * @ClassName UarCycleMaintnanceBean
 * @Author wangfenglong
 * @Date 2026/5/26 15:01
 **/
@Data
public class UarCycleMaintnanceBean
{
    private String email;
    Map<String, String> variables;
}
