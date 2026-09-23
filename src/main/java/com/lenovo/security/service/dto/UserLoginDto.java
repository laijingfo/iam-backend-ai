package com.lenovo.security.service.dto;


import com.lenovo.entity.User;

/**
 * @description 缓存用户时使用
 **/
public class UserLoginDto extends User {
    private String password;
    private Boolean isAdmin;

}
