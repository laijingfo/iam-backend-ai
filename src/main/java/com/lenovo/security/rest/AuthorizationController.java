package com.lenovo.security.rest;

import com.lenovo.bean.UserDomainBean;
import com.lenovo.config.LogOperation;
import com.lenovo.controller.BaseController;
import com.lenovo.entity.User;
import com.lenovo.security.config.bean.LoginProperties;
import com.lenovo.security.config.bean.SecurityProperties;
import com.lenovo.security.exception.BadRequestException;
import com.lenovo.security.security.TokenProvider;
import com.lenovo.security.service.OnlineUserService;
import com.lenovo.security.service.UserCacheManager;
import com.lenovo.security.service.dto.AuthUserDto;
import com.lenovo.security.service.dto.JwtUserDto;
import com.lenovo.security.service.dto.UserLoginDto;
import com.lenovo.security.service.dto.UserPassVo;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.RoleService;
import com.lenovo.service.UserService;
import com.lenovo.util.ValidUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 授权、根据token获取用户详细信息
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthorizationController extends BaseController
{
    private final SecurityProperties properties;
    private final OnlineUserService onlineUserService;
    private final TokenProvider tokenProvider;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final RoleService roleService;
    private final UserCacheManager userCacheManager;
    @Resource
    private LoginProperties loginProperties;


    /**
     * @Description TODO 暂时用不上也不调用，保留方法，防止接口404，如果用得上再开发
    **/
    @PostMapping(value = "/login")
    public ResponseEntity<?> login(@Validated @RequestBody AuthUserDto authUser, HttpServletRequest request) throws Exception
    {
        String password = authUser.getPassword();
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(authUser.getUsername(), password);
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = tokenProvider.createToken(authentication.getName());
        final JwtUserDto jwtUserDto = (JwtUserDto) authentication.getPrincipal();
        userService.getAdfsUser(authentication.getName());
        userCacheManager.addUserCache(authentication.getName(), jwtUserDto);
        // 保存在线信息
        onlineUserService.save(jwtUserDto, token, request);
        // 返回 token 与 用户信息
        Map<String, Object> authInfo = createAuthInfo(token, jwtUserDto, null);
        if (loginProperties.isSingleLogin())
        {
            //踢掉之前已经登录的token
            onlineUserService.checkLoginOnUser(authUser.getUsername(), token);
        }
        return ok(authInfo);
    }

    /**
     * @Description SAML授权登陆之后获取用户详细信息在调用该接口，参数是解析SAMLResponse获取的itCode
    **/
    @GetMapping("/adfs")
    @LogOperation(value = "用户登录", module = "登录", type = LogOperation.OperationType.LOGIN)
    public ResponseEntity<?> auth(@RequestParam String itcode, HttpServletRequest request)
    {
        UserDomainBean userDomain = userService.getUserDomain(itcode);
        if (null == userDomain)
        {
            throw new BadRequestException("ADFS error！");
        }
        UserLoginDto loginDto = new UserLoginDto();
        loginDto.setId(itcode);
        loginDto.setDateRange(userDomain.getCurrentDomain());
        loginDto.setUserName(itcode);
        loginDto.setItcode(itcode);
        loginDto.setMail(userDomain.getEmail());
        loginDto.setStatus(true);
        loginDto.setType("ADFS");
        List<String> organizeList = roleService.getOrganizeListByItcode(itcode);
        loginDto.setOrganizeRange(organizeList);
        JwtUserDto jwtUserDto = new JwtUserDto(loginDto, null, roleService.itcodeToGrantedAuthorities(itcode));
        userService.getAdfsUser(itcode);
        userCacheManager.addUserCache(itcode, jwtUserDto);
        String token = tokenProvider.createToken(itcode);
        // 保存在线信息
        onlineUserService.save(jwtUserDto, token, request);
        Map<String, Object> authInfo = createAuthInfo(token, jwtUserDto, organizeList);
        return ok(authInfo);
    }


    @PostMapping(value = "/resetPass")
    public ResponseEntity resetPass(@RequestBody UserPassVo passVo, HttpServletRequest request) throws Exception {
        String newPass = passVo.getNewPass();
        String oldPass = passVo.getOldPass();

        if (!ValidUtils.isPassword(newPass)) {
            throw new BadRequestException("Numbers and letters are allowed in the password!");
        }

        User user = userService.findByName(SecurityUtils.getCurrentUsername());
        if (!passwordEncoder.matches(oldPass, user.getPassword())) {
            throw new BadRequestException("The original password is incorrect!");
        }

        userService.updatePass(user.getUserName(), passwordEncoder.encode(newPass));
        onlineUserService.logout(tokenProvider.getToken(request));
        return ok("ok");
    }

    @GetMapping(value = "/info")
    public ResponseEntity getUserInfo() {
        return ok(SecurityUtils.getCurrentUser());
    }

    @GetMapping(value = "/adfsinfo")
    public ResponseEntity getAdfsCurrentUser() {
        return ok(SecurityUtils.getAdfsCurrentUser());
    }


    @DeleteMapping(value = "/logout")
    public ResponseEntity logout(HttpServletRequest request) {
        onlineUserService.logout(tokenProvider.getToken(request));
        return ok("ok");
    }


    @GetMapping("/createToken")
    public ResponseEntity createToken(@RequestParam String userName, HttpServletRequest request) {
        UserLoginDto loginDto = userService.getLoginData(userName);
        JwtUserDto jwtUserDto = new JwtUserDto(loginDto, null, roleService.mapToGrantedAuthorities(loginDto));
        userService.getAdfsUser(userName);
        userCacheManager.addUserCache(userName, jwtUserDto);
        String token = tokenProvider.createToken(userName);
        // 保存在线信息
        onlineUserService.save(jwtUserDto, token, request);

        Map<String, Object> authInfo = createAuthInfo(token, jwtUserDto, null);
        return ok(authInfo);
    }

    private Map<String, Object> createAuthInfo(String token, JwtUserDto user, List<String> organizeRange)
    {
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("roles", user.getRoles());
        userInfo.put("dateRange", user.getDateRange());
        userInfo.put("email", user.getEmail());
        userInfo.put("userType", user.getUserType());
        userInfo.put("username", user.getUsername());
        userInfo.put("enabled", user.isEnabled());
        if (organizeRange != null)
        {
            userInfo.put("organizeRange", organizeRange);
        }

        Map<String, Object> authInfo = new HashMap<>(2);
        authInfo.put("token", properties.getTokenStartWith() + token);
        authInfo.put("user", List.of(userInfo));
        return authInfo;
    }
}
