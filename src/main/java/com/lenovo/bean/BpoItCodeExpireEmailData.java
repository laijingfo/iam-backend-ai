package com.lenovo.bean;

import lombok.Data;

import java.util.Map;

/**
 * @Description TODO bpo的itcode失效发送邮件给focal和owner的邮件数据
 * @Author wangfenglong
 * @Date 2026/3/4 15:05
 **/
@Data
public class BpoItCodeExpireEmailData
{
    String emailName; //收件人itcode
    int appCount; //系统个数
    int accessCount; //用户数
    String email;
    Map<String, String> variables;//需要替换的邮件模版参数

    public BpoItCodeExpireEmailData(String emailName, int appCount, int accessCount)
    {
        this.emailName = emailName;
        this.appCount = appCount;
        this.accessCount = accessCount;
    }
}
