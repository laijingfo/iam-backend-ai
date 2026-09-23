package com.lenovo.controller;

import com.alibaba.fastjson.JSONObject;
import com.lenovo.adfs.exception.SAMLHandlerException;
import com.lenovo.config.LogOperation;
import com.lenovo.entity.User;
import com.lenovo.mapper.UserMapper;
import com.lenovo.security.saml.SamlResponseParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SamlController extends BaseController
{
    public static final String URL = "url";
    public static final String SAMLREQUEST = "SAMLRequest";
    private final UserMapper userMapper;
    @Value("${redirect_url}")
    private String REDIRECT_URL;


    /**
     * @Description  测试生产环境登陆第一次请求SAML授权获取用户信息userInfo解析出itcode，返回给业务系统调用AuthorizationController的/auth/adfs
    **/
    @ResponseBody
    @PostMapping("/auth/adfs")
    @LogOperation(value = "SAML授权", module = "SAML", type = LogOperation.OperationType.LOGIN)
    public ModelAndView auth(String SAMLResponse, HttpServletRequest request, HttpServletResponse response, ModelMap modelMap) throws URISyntaxException
    {
        boolean isSuccess = true;
        String userInfo = "";
        try
        {
            log.info("Received SAMLResponse (encoded length: {})",
                    SAMLResponse == null ? 0 : SAMLResponse.length());
            userInfo = SamlResponseParser.parse(SAMLResponse);
            HashMap jsonObject = JSONObject.parseObject(userInfo).toJavaObject(HashMap.class);
            String itcode = (String) jsonObject.get("itcode");
            if (StringUtils.isNotEmpty(itcode))
            {
                User user = userMapper.getAdfsUserByItcode(itcode);
                if (user == null)
                {
                    userMapper.addUser(jsonObject);
                }
            }
            log.info("SAML user authenticated: {}", itcode);
        }
        catch (SAMLHandlerException e)
        {
            isSuccess = false;
            log.error("SAML解析SAMLResponse失败：{}", e.getMessage(), e);
        }

        try
        {
            String itcode = Base64.getEncoder().encodeToString(userInfo.getBytes(StandardCharsets.UTF_8));
            if (isSuccess)
            {
                Map<String, String> attribute = new HashMap<>();
                attribute.put("token", Base64.getEncoder().encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8))); //该token无实际意义(应该需要二次校验，没有token时效性校检测，新旧信息都可以登录)
                attribute.put("userInfo", itcode);//前端真正需要的
                modelMap.addAllAttributes(attribute);
                return new ModelAndView(REDIRECT_URL, modelMap);
            }
            else
            {
                response.sendRedirect("/loginFail");
            }
        }
        catch (Exception e)
        {
            log.error("SAML授权失败：{}", e.getMessage(), e);
        }
        return null;
    }

    @RequestMapping("/loginFail")
    public String loginFail() {
        return "loginFail";
    }

    /**
     * 此接口的作用是模拟一下业务场景，比如获取用户信息
     * 当用户未登录时，调用index接口时会首先被拦截器拦截，如果发现session中没有用户登陆信息，则必须先去ADFS进行登录
     * 用户登录成功后，会存储用户信息，才可以访问业务接口
     *
     * @param request
     * @return
     */
    @RequestMapping("/index")
    public String index(HttpServletRequest request) {
        String userInfo = (String) request.getSession().getAttribute("userInfo");
        log.info("当前用户信息为：" + userInfo);
        JSONObject jsonObject = JSONObject.parseObject(userInfo);
        request.setAttribute("userName", jsonObject.get("itcode"));
        return "welcome";
    }
}
