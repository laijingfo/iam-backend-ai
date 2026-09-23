package com.lenovo.security.utils;

import cn.hutool.json.JSONObject;
import com.lenovo.security.exception.AuthException;
import com.lenovo.security.exception.BadRequestException;
import com.lenovo.util.SpringContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;


/**
 * 获取当前登录的用户
 */
@Slf4j
public class SecurityUtils {

    /**
     * 获取当前登录的用户
     *
     * @return UserDetails
     */
    public static UserDetails getCurrentUser() {
        UserDetailsService userDetailsService = SpringContextHolder.getBean(UserDetailsService.class);
        return userDetailsService.loadUserByUsername(getCurrentUsername());
    }

    public static UserDetails getAdfsCurrentUser() {
        UserDetailsService userDetailsService = SpringContextHolder.getBean(UserDetailsService.class);
        return userDetailsService.loadUserByUsername(getCurrentUsername(MaConstant.MA_LOGIN_KEY));
    }

    /**
     * 获取系统用户名称
     *
     * @return 系统用户名称
     */
    public static String getCurrentUsername() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AuthException("当前登录状态过期");
        }
        if (authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return userDetails.getUsername();
        }
        throw new AuthException("找不到当前登录的信息");
    }

    public static String getCurrentUsername(String suffix) {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AuthException("当前登录状态过期");
        }
        if (authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return userDetails.getUsername() + suffix;
        }
        throw new AuthException("找不到当前登录的信息");
    }

    /**
     * 获取系统用户ID
     *
     * @return 系统用户ID
     */
    public static String getCurrentUserId() {
        UserDetails userDetails = getCurrentUser();
        return new JSONObject(new JSONObject(userDetails).get("user")).get("id", String.class);
    }

    /**
     * 获取系统用户类型
     *
     * @return 系统用户类型
     */
    public static String getCurrentUserType() {
        UserDetails userDetails = getCurrentUser();
        return new JSONObject(new JSONObject(userDetails).get("user")).get("type", String.class);
    }

}
