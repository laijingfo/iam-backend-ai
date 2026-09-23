package com.lenovo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.entity.S3File;
import com.lenovo.service.S3FileService;
import com.lenovo.mapper.S3FileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
* @author mercury
* @description 针对表【s3_file】的数据库操作Service实现
* @createDate 2024-10-23 11:45:11
*/
@Service
public class S3FileServiceImpl extends ServiceImpl<S3FileMapper, S3File>
    implements S3FileService{


    @Transactional
    public Integer safes3_fileGetNextId() {
        QueryWrapper<S3File> wrapper = new QueryWrapper<>();
        wrapper.select("MAX(id)");

        Integer maxId = getBaseMapper().selectObjs(wrapper).stream()
                .filter(java.util.Objects::nonNull)
                .map(value -> ((Number) value).intValue())
                .findFirst()
                .orElse(0);

        return maxId + 1;
    }
}




