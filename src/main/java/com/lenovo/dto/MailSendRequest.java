package com.lenovo.dto;

import com.lenovo.bean.UseAccessReviewBean;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MailSendRequest {
    private List<String> sequenceNumbers; //单选：前端传sequenceNumbers。全选：前端不传sequenceNumbers
    private Boolean isALL;//全选：true。单选false
    private UseAccessReviewBean filters; // 过滤条件
}
