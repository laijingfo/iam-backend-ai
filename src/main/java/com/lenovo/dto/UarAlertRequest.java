package com.lenovo.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UarAlertRequest {
    private String cmdbId;
    private String appName;
    /**告警类型:
     * 失效
     *      10:user ItCode；
     *      11:line_manager ItCode；
     *      12:bpo ItCode；
     * 变更
     *      21:line_manager ；
     * 手动失效
     *      32:bpo 手动失效
     * */
    private String alertType;
    private String itCodeOfUser;
    private String lineManager;
    private String bpo;
    private String alertHandleStatus; // 告警状态，可接受多个类型，逗号分割
    private String updateBy;
    private LocalDateTime updateTime;
    private String operationOwner;
    private String operationFocal;


    /** 基础查询参数 */
    private List<String> dataRange;
    private String sortField;
    private String sortOrder;
    private Integer page;
    private Integer size;

    /** 初始值 */
    public UarAlertRequest() {
        this.page = 1;
        this.size = 10;
    }

    /** 导出字段 */
    /** 语言 */
    private String language;
    /** 模块 */
    private String module;

}
