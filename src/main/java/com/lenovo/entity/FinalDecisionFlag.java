package com.lenovo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("final_decision_flag")
public class FinalDecisionFlag {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 最终审核状态标志
     */
    private Boolean flag;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;

    /**
     * 更新人
     */
    private String updatedBy;
}