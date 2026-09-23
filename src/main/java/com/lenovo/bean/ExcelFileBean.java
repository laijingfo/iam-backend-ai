package com.lenovo.bean;

import lombok.Data;

@Data
public class ExcelFileBean {
    private String fileName;      // xxx.xlsx
    private Long fileSize;        // 字节数
    private String mimeType;      // application/vnd...
    private byte[] fileContent;   // 二进制内容
}