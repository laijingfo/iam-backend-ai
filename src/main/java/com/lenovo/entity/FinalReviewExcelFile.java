package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("final_review_excel_file")
public class FinalReviewExcelFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileName;
    private Long fileSize;
    private String mimeType;
    private String itCode;
    /**
     * 二进制文件
     * BYTEA ↔ byte[] */
    private byte[] fileContent;
    private LocalDateTime createTime;
    private String createBy;
}