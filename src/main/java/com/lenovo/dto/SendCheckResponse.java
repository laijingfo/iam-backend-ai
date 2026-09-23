package com.lenovo.dto;

import lombok.Data;

@Data
public class SendCheckResponse {
    private boolean flag; // true:超出限制，false:未超出限制
    private int linemanager; // Line Manager 邮件数量
    private int bpo; // BPO 邮件数量
    private int total; // 总邮件数量
    private int selectedCount; // 选中记录数量
    private String message; // 附加信息

    public SendCheckResponse() {}

    public SendCheckResponse(boolean flag, int linemanager, int bpo, int total, int selectedCount) {
        this.flag = flag;
        this.linemanager = linemanager;
        this.bpo = bpo;
        this.total = total;
        this.selectedCount = selectedCount;
        this.message = flag ? "邮件数量超出限制（最多10000封）" : "邮件数量在限制范围内";
    }
}