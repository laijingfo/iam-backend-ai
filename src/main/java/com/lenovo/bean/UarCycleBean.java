package com.lenovo.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.lenovo.constant.AccessReviewScope;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UarCycleBean {
    // 用来接受传入的修改数据
    // 通用参数 cmdbId
    private List<String> cmdbId;

    // 这里参数应该没用，但是一直在，先保留
    private String cycleDataFlag;

    // ==start== 开启周期时，组装参数。为了拿到主键ID
    private Long id;
    private String uarId;
    private String uarName;
    private LocalDate uarCycleStartDate;
    private LocalDate uarCycleEndDate;
    private AccessReviewScope accessReviewScope;
    // ==end==


    // 下线用的参数： 下线原因
    private String decommissionReason;

    // 是否全选
    private Boolean isALL;
    // filters是用来查询的，依据isALL，这里的Bean就是列表的查询条件
    private UarCycleMaintenanceBean filters;



}
