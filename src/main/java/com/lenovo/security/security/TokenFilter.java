package com.lenovo.security.security;

import cn.hutool.core.util.StrUtil;
import com.lenovo.security.config.bean.SecurityProperties;
import com.lenovo.security.service.OnlineUserService;
import com.lenovo.security.service.dto.OnlineUserDto;
import jakarta.servlet.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class TokenFilter extends GenericFilterBean
{
    private final TokenProvider tokenProvider;
    private final SecurityProperties properties;
    private final OnlineUserService onlineUserService;

    /**
     * @param tokenProvider     Token
     * @param properties        JWT
     * @param onlineUserService 用户在线
     */
    public TokenFilter(TokenProvider tokenProvider, SecurityProperties properties, OnlineUserService onlineUserService)
    {
        this.properties = properties;
        this.onlineUserService = onlineUserService;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException
    {

        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        // === 关键：async dispatch 直接放行，不再走鉴权 ===
        // SseEmitter.complete() 会触发一次 ASYNC dispatch，
        // 此时响应已提交，再走鉴权会覆盖/阻塞 SSE 数据
        if (httpServletRequest.getDispatcherType() == DispatcherType.ASYNC) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        String token = resolveToken(httpServletRequest);
        // 对于 Token 为空的不需要去查 Redis
        if (StrUtil.isNotBlank(token))
        {
            OnlineUserDto onlineUserDto = onlineUserService.getOne(properties.getOnlineKey() + token);

            // Token 过期或 Redis 中已不存在会话时，终止当前请求。
            if (onlineUserDto == null)
            {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "当前登录状态过期");
                return;
            }
            //处理token有效的情况，建立认证信息并可能续期token
            //onlineUserDto不为null（Redis中存在有效的在线用户记录）且token不为空
            if (onlineUserDto != null && StringUtils.hasText(token))
            {
                Authentication authentication = tokenProvider.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                // Token 续期
                tokenProvider.checkRenewal(token);
            }
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    /**
     * 初步检测Token
     *
     * @param request /
     * @return /
     */
    private String resolveToken(HttpServletRequest request)
    {
        String bearerToken = request.getHeader(properties.getHeader());
        if(StringUtils.hasText(bearerToken) && bearerToken.startsWith(properties.getTokenStartWith()))
        {
            // 去掉令牌前缀
            return bearerToken.replace(properties.getTokenStartWith(), "");
        }
        return null;
    }
}
