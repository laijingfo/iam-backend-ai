package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lenovo.entity.FinalReviewExcelFile;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
public interface FinalReviewExcelFileMapper extends BaseMapper<FinalReviewExcelFile>
{

    List<Map<String, Object>> selectAllExcel();

    void clearFinalReviewExcelFile();
}
