package com.lenovo.bean.apihub;

import com.lenovo.entity.LenovoUser;
import lombok.Data;

import java.util.List;

@Data
public class UserProfileData {
    private Integer currPage;

    private List<LenovoUser> list;

    private Integer pageSize;

    private Integer totalCount;

    private Integer totalPage;
}
