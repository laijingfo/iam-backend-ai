package com.lenovo.bean;

import lombok.Data;

import java.util.List;

/**
 * @Description TODO 邮件发送失败校验查询当前账户角色Bean
 * @Author wangfenglong
 * @Date 2025/12/16 15:24
 **/
@Data
public class EmailFailedBean
{
    private String roleFlag;
    private List<String> cmdbIdList;
}
