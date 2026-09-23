package com.lenovo.security.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lenovo.util.RedisUtils;
import com.lenovo.security.config.bean.LoginProperties;
import com.lenovo.security.config.bean.SecurityProperties;
import com.lenovo.security.service.dto.AuthorityDto;
import com.lenovo.security.service.dto.JwtUserDto;
import com.lenovo.security.service.dto.UserLoginDto;
import com.lenovo.security.utils.MaConstant;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * @description 用户缓存管理
 **/
@Component
public class UserCacheManager {

    @Resource
    private RedisUtils redisUtils;
    @Resource
    private SecurityProperties securityProperties;

    /**
     * 返回用户缓存
     *
     * @param userName 用户名
     * @return JwtUserDto
     */
    public JwtUserDto getUserCache(String userName) {
        if (StringUtils.isNotEmpty(userName)) {
            if (userName.indexOf(MaConstant.MA_LOGIN_KEY) > 0) {
                userName = userName.replace(MaConstant.MA_LOGIN_KEY, "").trim();
            }
            // 获取数据
            Object obj = redisUtils.get(cacheKey(userName));
            if (obj instanceof JwtUserDto jwtUserDto) {
                return jwtUserDto;
            }
            if (obj != null) {
                UserLoginDto userLoginDto = JSONObject.parseObject(JSONObject.parseObject(JSON.toJSONString(obj)).get("user").toString(), UserLoginDto.class);
                List<AuthorityDto> authorities = JSONObject.parseArray(JSONObject.parseObject(JSON.toJSONString(obj)).get("authorities").toString(), AuthorityDto.class);

                return new JwtUserDto(userLoginDto, null, authorities);
            }
        }
        return null;
    }

    /**
     * 添加缓存到Redis
     *
     * @param userName 用户名
     */
    public void addUserCache(String userName, JwtUserDto user) {
        if (StringUtils.isNotEmpty(userName)) {
            redisUtils.set(cacheKey(userName), user,
                    securityProperties.getTokenValidityInSeconds() / 1000);
        }
    }

    /**
     * 清理用户缓存信息
     * 用户信息变更时
     *
     * @param userName 用户名
     */
    public void cleanUserCache(String userName) {
        if (StringUtils.isNotEmpty(userName)) {
            // 清除数据
            redisUtils.del(cacheKey(userName));
        }
    }

    private String cacheKey(String userName) {
        return LoginProperties.cacheKey + userName;
    }
}
