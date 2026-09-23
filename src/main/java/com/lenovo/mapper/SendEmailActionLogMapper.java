package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lenovo.entity.SendEmailActionLog;
import org.apache.ibatis.annotations.Param;

/**
 * @Description TODO
 * @ClassName SendEmailActionLogMapper
 * @Author wangfenglong
 * @Date 2026/1/20 16:31
 **/
public interface SendEmailActionLogMapper extends BaseMapper<SendEmailActionLog>
{
    void addSendEmailLog(@Param("SendEmailActionLog") SendEmailActionLog sendEmailActionLog);
}
