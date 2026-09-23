package com.lenovo.bean.apihub;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

@Data
public class ApiHubTokenBean {
    @JSONField(name = "access_token")
    private String accessToken;
    @JSONField(name = "refresh_token")
    private String refreshToken;
    @JSONField(name = "expires_in")
    private Integer expiresIn;
    @JSONField(name = "refresh_expires_in")
    private Integer refreshExpiresIn;
    @JSONField(name = "token_type")
    private String tokenType;
    @JSONField(name = "scope")
    private String scope;
}
