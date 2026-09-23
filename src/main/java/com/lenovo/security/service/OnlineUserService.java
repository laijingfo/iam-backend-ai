package com.lenovo.security.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lenovo.util.RedisUtils;
import com.lenovo.security.config.bean.SecurityProperties;
import com.lenovo.security.service.dto.JwtUserDto;
import com.lenovo.security.service.dto.OnlineUserDto;
import com.lenovo.security.utils.EncryptUtils;
import com.lenovo.security.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class OnlineUserService {

    private final SecurityProperties properties;
    private final RedisUtils redisUtils;

    public OnlineUserService(SecurityProperties properties, RedisUtils redisUtils) {
        this.properties = properties;
        this.redisUtils = redisUtils;
    }

    /**
     * 保存在线用户信息
     *
     * @param jwtUserDto /
     * @param token      /
     * @param request    /
     */
    public void save(JwtUserDto jwtUserDto, String token, HttpServletRequest request) {
        String ip = StringUtils.getIp(request);
        String browser = StringUtils.getBrowser(request);
        String address = StringUtils.getCityInfo(ip);
        OnlineUserDto onlineUserDto = null;
        try {
            onlineUserDto = new OnlineUserDto(jwtUserDto.getUsername(), jwtUserDto.getUser().getUserName(), browser, ip, address, EncryptUtils.desEncrypt(token), new Date());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        redisUtils.set(properties.getOnlineKey() + token, onlineUserDto, properties.getTokenValidityInSeconds() / 1000);
    }

    /**
     * 查询全部数据，不分页
     *
     * @param filter /
     * @return /
     */
    public List<OnlineUserDto> getAll(String filter) {
        List<String> keys = redisUtils.scan(properties.getOnlineKey() + "*");
        Collections.reverse(keys);
        List<OnlineUserDto> onlineUserDtos = new ArrayList<>();
        for (String key : keys) {
            OnlineUserDto onlineUserDto = (OnlineUserDto) redisUtils.get(key);
            if (StringUtils.isNotBlank(filter)) {
                if (onlineUserDto.toString().contains(filter)) {
                    onlineUserDtos.add(onlineUserDto);
                }
            } else {
                onlineUserDtos.add(onlineUserDto);
            }
        }
        onlineUserDtos.sort((o1, o2) -> o2.getLoginTime().compareTo(o1.getLoginTime()));
        return onlineUserDtos;
    }

    /**
     * 踢出用户
     *
     * @param key /
     */
    public void kickOut(String key) {
        key = properties.getOnlineKey() + key;
        redisUtils.del(key);
    }
    /**
     * 踢出用户
     *
     */
    public void kickOutALl() {
        List<String> scan = redisUtils.scan(properties.getOnlineKey() + "*");
        scan.forEach(item->{
            redisUtils.del(item);
        });
    }

    /**
     * 退出登录
     *
     * @param token /
     */
    public void logout(String token) {
        String key = properties.getOnlineKey() + token;
        redisUtils.del(key);
    }


    /**
     * 查询用户
     *
     * @param key /
     * @return /
     */
    public OnlineUserDto getOne(String key) {
        return JSONObject.parseObject(JSON.toJSONString(redisUtils.get(key)), OnlineUserDto.class);
    }

    /**
     * 检测用户是否在之前已经登录，已经登录踢下线
     *
     * @param userName 用户名
     */
    public void checkLoginOnUser(String userName, String igoreToken) {
        List<OnlineUserDto> onlineUserDtos = getAll(userName);
        if (onlineUserDtos == null || onlineUserDtos.isEmpty()) {
            return;
        }
        for (OnlineUserDto onlineUserDto : onlineUserDtos) {
            if (onlineUserDto.getUserName().equals(userName)) {
                try {
                    String token = EncryptUtils.desDecrypt(onlineUserDto.getKey());
                    if (StringUtils.isNotBlank(igoreToken) && !igoreToken.equals(token)) {
                        this.kickOut(token);
                    } else if (StringUtils.isBlank(igoreToken)) {
                        this.kickOut(token);
                    }
                } catch (Exception e) {
                    log.error("checkUser is error", e);
                }
            }
        }
    }

}
