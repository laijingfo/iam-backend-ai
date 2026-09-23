package com.lenovo.service;

import com.lenovo.entity.S3File;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author mercury
* @description 针对表【s3_file】的数据库操作Service
* @createDate 2024-10-23 11:45:11
*/
public interface S3FileService extends IService<S3File> {

    public Integer safes3_fileGetNextId();

}
