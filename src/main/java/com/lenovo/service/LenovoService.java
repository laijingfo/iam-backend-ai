package com.lenovo.service;

import com.lenovo.entity.LenovoUser;

public interface LenovoService {

    /**
     * 同步用户数据, 并且对旧数据备份
     */
    void syncUserProfileData();

    LenovoUser getUserInfoByItCode(String itCode);
}
