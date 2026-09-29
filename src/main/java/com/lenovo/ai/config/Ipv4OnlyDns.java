package com.lenovo.ai.config;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Dns;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-29 10:20
 * @project iam-backend-ai
 * @description
 * 只返回 IPv4 地址的 DNS 解析器，用于规避"双栈域名 + 本机无 IPv6 出口"导致的连接失败。
 *
 * <p>背景：阿里云 MaaS 这类域名（例如 ws-xxx.cn-beijing.maas.aliyuncs.com）通过 CNAME 指向
 * NLB，会同时下发 A(IPv4) 与 AAAA(IPv6) 记录。当办公网/容器只有 IPv4 出口时，OkHttp 5.x
 * 默认的 Happy-Eyeballs 实现（{@code FastFallbackExchangeFinder}）依然会去尝试 IPv6，
 * 而链路上的中间设备往往直接在 TLS 握手阶段回 RST，最终表现为
 * {@code java.net.SocketException: Connection reset}。因为每次重试都命中同一条坏路径，
 * langchain4j 的重试完全无效，只能一路失败到抛异常。
 *
 * <p>这里在解析阶段就把 AAAA 结果过滤掉，让连接只走 IPv4。
 * 若某个域名确实只有 IPv6 记录（纯 IPv6 环境、或本机 {@code ::1}），则原样返回，
 * 避免过度过滤造成硬性的解析失败。
 */
@Slf4j
public class Ipv4OnlyDns implements Dns {

    private final Dns delegate;

    public Ipv4OnlyDns() {
        this(Dns.SYSTEM);
    }

    public Ipv4OnlyDns(Dns delegate) {
        this.delegate = delegate == null ? Dns.SYSTEM : delegate;
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        List<InetAddress> resolved = delegate.lookup(hostname);
        if (resolved == null || resolved.isEmpty()) {
            return List.of();
        }

        List<InetAddress> ipv4 = new ArrayList<>(resolved.size());
        for (InetAddress address : resolved) {
            if (address instanceof Inet4Address) {
                ipv4.add(address);
            }
        }

        if (!ipv4.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("DNS {} 过滤掉 {} 条 IPv6 记录，仅使用 IPv4：{}",
                        hostname, resolved.size() - ipv4.size(), ipv4);
            }
            return ipv4;
        }

        log.warn("DNS {} 只解析出 IPv6 记录 {}，已按原样返回；若本机无 IPv6 出口，连接将失败",
                hostname, resolved);
        return resolved;
    }
}
