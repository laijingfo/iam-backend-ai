package com.lenovo.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lenovo.bean.apihub.ApiHubTokenBean;
import com.lenovo.bean.apihub.UserProfileData;
import com.lenovo.constant.ApiParamsConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiHubUtils {
    @Value("${api-hub.domain:}")
    private String domain;

    @Value("${api-hub.username:}")
    private String username;

    @Value("${api-hub.password:}")
    private String password;

    @Value("${api-hub.x-api-key:}")
    private String xApiKey;

    @Value("${spring.profiles.active:}")
    private String env;
    @Value("${api-hub-na.domain:}")
    private String naDomain;

    @Value("${api-hub-na.username:}")
    private String naUsername;

    @Value("${api-hub-na.password:}")
    private String naPassword;

    @Value("${api-hub-na.x-api-key:}")
    private String naxApiKey;

    @Value("${api-hub-na.secAuthorization:}")
    private String naSecAuthorization;

    @Value("${api-hub-na.app-name:}")
    private String naAppName;

    static String ACCESS_TOKEN = "api_hub:access_token";
    static String REFRESH_TOKEN = "api_hub:refresh_token";
    static List<String> PRODUCTION_ENV = List.of("prod", "prod-na");
    static String NA_ACCESS_TOKEN = "api_hub_na:access_token";
    static String NA_REFRESH_TOKEN = "api_hub_na:refresh_token";
    private final RedisUtils redisUtils;


    /**
     * 获取有效的 token
     * @return tokenBean
     */
    public ApiHubTokenBean getValidToken(String username, String password, String domain, String xApiKey,String accessToken,String refreshToken) {
        ApiHubTokenBean tokenBean = getTokenFromRedis(accessToken,refreshToken);
        if (tokenBean.getAccessToken() == null || tokenBean.getRefreshToken() == null) {
            // 如果没有有效的 access_token 或 refresh_token，重新获取 token
            getToken(username, password, domain, xApiKey, accessToken, refreshToken);
            return getTokenFromRedis(accessToken,refreshToken);
        }

        // 检查 access_token 是否过期
        String newAccessToken = tokenBean.getAccessToken();
        if (newAccessToken == null || newAccessToken.isEmpty()) {
            // 如果 access_token 无效，尝试刷新
            return getRefreshToken(username, password, domain, xApiKey, accessToken, refreshToken);
        }

        return tokenBean;
    }

    /**
     * 获取 token
     */
    public void getToken(String username, String password, String domain, String xApiKey,String accessToken,String refreshToken) {
        log.info("----{}----", "获取Token");
        String path = domain + "/token";

        HashMap<String, String> header = new HashMap<>();
        header.put("Content-Type", "application/x-www-form-urlencoded");
        header.put("X-API-KEY", xApiKey);

        HashMap<String, String> body = new HashMap<>();
        body.put("password", password);
        body.put("username", username);

        String httpPost = HttpUtilsSkpSsl.getHttpPost(path, header, body);

        ApiHubTokenBean tokenBean = parseJsonToObject(httpPost, ApiHubTokenBean.class);
        if (tokenBean != null) {
            saveTokenToRedis(tokenBean,accessToken,refreshToken);
        } else {
            throw new RuntimeException("Failed to get new token.");
        }
    }

    /**
     * 获取刷新 token
     * @return tokenBean
     */
    public ApiHubTokenBean getRefreshToken(String username, String password, String domain, String xApiKey,String accessToken,String refreshToken) {
        log.info("----{}----", "刷新Token");
        ApiHubTokenBean tokenBean = getTokenFromRedis(accessToken,refreshToken);
        if (tokenBean.getRefreshToken() == null) {
            // 如果没有有效的 refresh_token，重新获取 token
            getToken(username, password, domain, xApiKey, accessToken, refreshToken);
            return getTokenFromRedis(accessToken,refreshToken);
        }

        String path = domain + "/token";
        HashMap<String, String> header = new HashMap<>();
        header.put("Content-Type", "application/x-www-form-urlencoded");
        header.put("X-API-KEY", xApiKey);

        HashMap<String, String> body = new HashMap<>();
        body.put("refresh_token", tokenBean.getRefreshToken());
        body.put("grant_type", "refresh_token");

        String httpPost = HttpUtilsSkpSsl.getHttpPost(path, header, body);
        ApiHubTokenBean refreshedTokenBean = parseJsonToObject(httpPost, ApiHubTokenBean.class);

        if (refreshedTokenBean != null) {
            saveTokenToRedis(refreshedTokenBean,accessToken,refreshToken);
            return refreshedTokenBean;
        } else {
            // 如果刷新失败，重新获取 token
            getToken(username, password, domain, xApiKey, accessToken, refreshToken);
            return getTokenFromRedis(accessToken,refreshToken);
        }
    }

    /**
     * 保存token
     * @param tokenBean tokenBean
     */
    private void saveTokenToRedis(ApiHubTokenBean tokenBean,String accessToken,String refreshToken) {
        // 存储 access_token
        redisUtils.setString(accessToken, tokenBean.getAccessToken(), tokenBean.getExpiresIn());
        // 存储 refresh_token
        redisUtils.setString(refreshToken, tokenBean.getRefreshToken(), tokenBean.getRefreshExpiresIn());
    }
    /**
     * 从redis中获取token
     * @return tokenBean
     */
    private ApiHubTokenBean getTokenFromRedis(String accessToken,String refreshToken) {
        ApiHubTokenBean tokenBean = new ApiHubTokenBean();
        tokenBean.setAccessToken(redisUtils.getString(accessToken));
        tokenBean.setRefreshToken(redisUtils.getString(refreshToken));
        return tokenBean;
    }


    /**
     * json 转对象
     *
     * @param jsonString json字符串
     * @param classType 对象类型
     * @param <T> 泛型
     * @return 对象
     */
    public <T> T parseJsonToObject(String jsonString, Class<T> classType) {
        try {
            return JSON.parseObject(jsonString, classType);
        } catch (RuntimeException e) {
            log.error("JSON parsing failed, target type: {}", classType.getName(), e);
            return null;
        }
    }



    /**
     * 获取iuop用户信息
     */
    public UserProfileData getIUOP_UserProfile(Integer page, Integer pageSize) {
        log.info("----{}----", "获取iuop用户信息");
        ApiHubTokenBean tokenBean = getValidToken(username, password, domain, xApiKey,ACCESS_TOKEN,REFRESH_TOKEN);
        if (tokenBean == null) {
            throw new RuntimeException("Failed to get valid token.");
        }

        String IUOP = "v1.0/analytics/iuop/api/v2/api-portal";
        String path = String.format( "%s/%s/%s", domain, PRODUCTION_ENV.contains(env) ? "prod" : "uat", IUOP);

        HashMap<String, String> header = new HashMap<>();
        header.put("X-API-KEY", xApiKey);
        header.put("Authorization", "Bearer " + tokenBean.getAccessToken());

        HashMap<String, Object> body = new HashMap<>();
        body.put("apiName", "UserProfileForUAR");
        body.put("app", "UAR");
        body.put("page", Map.of("page", page, "pageSize", pageSize));
        String jsonBody = JSON.toJSONString(body);

        String httpPost = HttpUtilsSkpSsl.getHttpPost(path, header, jsonBody);

        // 解析返回结果
        try {
            JSONObject jsonResponse = JSON.parseObject(httpPost);
            if (jsonResponse.getIntValue("code") == 0) {
                return jsonResponse.getObject("data", UserProfileData.class);
            } else {
                throw new RuntimeException("Failed to get json data: " + jsonResponse.getString("message"));
            }
        } catch (Exception e) {
            log.error("Failed to parse json data. json: {}", httpPost, e);
            throw new RuntimeException("Failed to parse json data.");
        }
    }

    /**
     * 同步na itsi数据
     * @param pageIndex
     * @param pageSize
     * @return
     */
    public String getNaItsApplicationData(Integer pageIndex, Integer pageSize,String search) {
        ApiHubTokenBean tokenBean = getValidToken(naUsername, naPassword, naDomain,naxApiKey,NA_ACCESS_TOKEN,NA_REFRESH_TOKEN);
        if (tokenBean == null) {
            throw new RuntimeException("Failed to get valid token.");
        }

        String path = naDomain + "/prod/v1.0/services/itsi/search/servicesNS/-/"+naAppName+"/search/jobs" +
                "?output_mode=json&max_count="+pageSize;
        HashMap<String, String> header = new HashMap<>();
        header.put("X-API-KEY", naxApiKey);
        header.put("secAuthorization", "Bearer " + naSecAuthorization);
        header.put("Authorization", "Bearer " + tokenBean.getAccessToken());

        HashMap<String, String> body = new HashMap<>();
        body.put("search", search);
        body.put("count", pageSize.toString());
        body.put("exec_mode", "oneshot");
        body.put("offset", String.valueOf(pageIndex));

        return HttpUtilsSkpSsl.getHttpPost(path, header, body);
    }
}
