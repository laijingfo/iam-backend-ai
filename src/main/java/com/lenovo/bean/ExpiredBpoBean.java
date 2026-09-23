package com.lenovo.bean;

import com.lenovo.entity.UarMailTemplate;
import lombok.Data;

import java.util.List;

/**
 * @Description TODO 给BPO失效的itCode发送邮件(发给focal和owner)发送邮件需要的参数
 * @ClassName ExpiredBpoBean
 * @Author wangfenglong
 * @Date 2026/4/23 10:07
 **/
@Data
public class ExpiredBpoBean
{
    private String itCode;
    private UarMailTemplate bpoTemplate;
    private String batchNo;
    private String sendFlag;
    private List<BpoItCodeExpireEmailData> dataList;
}
