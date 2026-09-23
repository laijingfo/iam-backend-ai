package com.lenovo.security.service;

import com.lenovo.security.exception.BadRequestException;
import com.lenovo.security.exception.EntityNotFoundException;
import com.lenovo.security.service.dto.JwtUserDto;
import com.lenovo.security.service.dto.UserLoginDto;
import com.lenovo.security.utils.MaConstant;
import com.lenovo.service.RoleService;
import com.lenovo.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;



@Slf4j
@RequiredArgsConstructor
@Service("userDetailsService")
public class UserDetailsServiceImpl implements UserDetailsService {
    private final UserService userService;
    private final RoleService roleService;
    private final UserCacheManager userCacheManager;

    @Override
    public JwtUserDto loadUserByUsername(String username) {
        JwtUserDto jwtUserDto = userCacheManager.getUserCache(username);

        if (username.indexOf(MaConstant.MA_LOGIN_KEY) > 0 && null == jwtUserDto) {
            throw new BadRequestException(HttpStatus.FORBIDDEN,"找不到当前登录的信息！");
        }

        if (jwtUserDto == null) {
            UserLoginDto user;
            try {
                user = userService.getLoginData(username);
            } catch (EntityNotFoundException e) {
                // SpringSecurity会自 动转换UsernameNotFoundException为BadCredentialsException
                throw new UsernameNotFoundException(username, e);
            }
            if (user == null) {
                throw new UsernameNotFoundException("");
            } else {
                if (!user.getStatus()) {
                    throw new BadRequestException("账号未激活！");
                }
                jwtUserDto = new JwtUserDto(
                        user,
                        null,
                        roleService.mapToGrantedAuthorities(user)
                );
                // 添加缓存数据
                userCacheManager.addUserCache(username, jwtUserDto);
            }
        }
        return jwtUserDto;
    }


}
