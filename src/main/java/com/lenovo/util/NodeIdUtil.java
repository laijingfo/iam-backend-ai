package com.lenovo.util;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import org.springframework.core.env.Environment;

/**
 * @Description TODO 每个节点生成唯一 ID，用于标记抢占的邮件归属，避免重复
 * @ClassName NodeIdUtil
 * @Author wangfenglong
 * @Date 2025/12/26 10:28
 **/
@Component
public class NodeIdUtil
{
    @Autowired
    private Environment environment;

    private String nodeId;

    @PostConstruct
    public void init()
    {
        try
        {
            // 获取本机IP + 应用端口作为唯一标识
            InetAddress localHost = InetAddress.getLocalHost();
            String ip = localHost.getHostAddress();
            // 读取Spring Boot端口（未配置则默认8080）
            String port = environment.getProperty("server.port", "8080");
            nodeId = ip + ":" + port;
        }
        catch (UnknownHostException e)
        {
            // 兜底：用UUID避免重复
            nodeId = UUID.randomUUID().toString().substring(0, 16);
        }
    }

    public String getNodeId()
    {
        return nodeId;
    }
}
