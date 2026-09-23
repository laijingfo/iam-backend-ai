package com.lenovo.security.security;

import cn.hutool.core.util.IdUtil;
import com.lenovo.config.GlobalBusinessStatusEnum;
import com.lenovo.security.config.bean.LoginProperties;
import com.lenovo.util.RedisUtils;
import com.lenovo.security.config.bean.SecurityProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import javax.crypto.SecretKey;
import java.util.ArrayList;

@Slf4j
@Component
public class TokenProvider implements InitializingBean
{
    public static final String AUTHORITIES_KEY = "user";
    private final SecurityProperties properties;
    private final RedisUtils redisUtils;
    private JwtParser jwtParser;
    private SecretKey signingKey;
    public TokenProvider(SecurityProperties properties, RedisUtils redisUtils)
    {
        this.properties = properties;
        this.redisUtils = redisUtils;
    }

    @Override
    public void afterPropertiesSet()
    {
        byte[] keyBytes = Decoders.BASE64.decode(properties.getBase64Secret());
        signingKey = Keys.hmacShaKeyFor(keyBytes);
        jwtParser = Jwts.parser().verifyWith(signingKey).build();
    }

    /**
     * 创建Token 设置永不过期，
     * Token 的时间有效性转到Redis 维护
     *
     * @return /
     */
    public String createToken(String userName)
    {
        return Jwts.builder()
                // 加入ID确保生成的 Token 都不一致
                .id(IdUtil.simpleUUID())
                .claim(AUTHORITIES_KEY, userName)
                .subject(userName)
                .signWith(signingKey, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * 依据Token 获取鉴权信息
     *
     * @param token /
     * @return /
     */
    Authentication getAuthentication(String token)
    {
        Claims claims = getClaims(token);
        User principal = new User(claims.getSubject(), "******", new ArrayList<>());
        return new UsernamePasswordAuthenticationToken(principal, token, new ArrayList<>());
    }

    public Claims getClaims(String token)
    {
        return jwtParser.parseSignedClaims(token).getPayload();
    }

    /**
     * @param token 需要检查的token
     */
    public void checkRenewal(String token)
    {
        // Redis 返回秒；配置的续期检查窗口使用毫秒。
        long remainingSeconds = redisUtils.getExpire(properties.getOnlineKey() + token);
        if (remainingSeconds > 0 && remainingSeconds * 1000 <= properties.getDetect())
        {
            redisUtils.expire(properties.getOnlineKey() + token, properties.getRenew());
            String userName = getClaims(token).getSubject();
            redisUtils.expire(userName + GlobalBusinessStatusEnum.REDIS_KEY_USER_ROLE.desc,
                    properties.getRenew());
            redisUtils.expire(LoginProperties.cacheKey + userName, properties.getRenew());
        }
    }


    public String getToken(HttpServletRequest request) {
        final String requestHeader = request.getHeader(properties.getHeader());
        if (requestHeader != null && requestHeader.startsWith(properties.getTokenStartWith())) {
            return requestHeader.substring(7);
        }
        return null;
    }
}
