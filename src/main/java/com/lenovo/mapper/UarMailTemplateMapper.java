package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.UarMailTemplateBean;
import com.lenovo.entity.UarMailTemplate;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UarMailTemplateMapper extends BaseMapper<UarMailTemplate> {
    Page<UarMailTemplate> query(Page p, @Param("bean") UarMailTemplateBean bean);

    /**
     * 获取指定tag和toSomeone的最大版本号
     */
    Integer getMaxVersionByTagAndToSomeone(@Param("tag") String tag, @Param("toSomeone") String toSomeone);

    /**
     * 获取全局最大版本号（tag为空或空字符串）
     */
    Integer getMaxGlobalVersion();

    // 新增方法：获取特定tag和toSomeone的所有模板
    List<UarMailTemplate> getTemplatesByTagAndToSomeone(@Param("tag") String tag, @Param("toSomeone") String toSomeone);

    //获取to_someone=ToUarProcessor,ToLineManager,ToBPO,ToUser,ToDelegatee的最大版本号的数据
    Page<UarMailTemplate> getMaxVersion(Page p, @Param("bean") UarMailTemplateBean bean);
}