package com.lenovo.service;

import com.lenovo.entity.FormFile;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
* @author mercury
* @description 针对表【form_file】的数据库操作Service
* @createDate 2024-10-23 11:45:11
*/
public interface FormFileService extends IService<FormFile> {

    FormFile getAndRefresh(Long id);

    FormFile uploadFile(MultipartFile file, String fileType, Integer workspaceId);
}
