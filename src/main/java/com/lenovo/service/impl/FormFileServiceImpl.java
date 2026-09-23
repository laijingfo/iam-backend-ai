package com.lenovo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.entity.FormFile;
import com.lenovo.entity.S3File;
import com.lenovo.service.FormFileService;
import com.lenovo.mapper.FormFileMapper;
import com.lenovo.service.S3FileService;
import com.lenovo.util.AmazonUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * @author mercury
 * @description 针对表【form_file】的数据库操作Service实现
 * @createDate 2024-10-23 11:45:11
 */
@Service
@RequiredArgsConstructor
public class FormFileServiceImpl extends ServiceImpl<FormFileMapper, FormFile>
        implements FormFileService {

    private final String FILE_BUCKET = "form-file1";
    private final S3FileService s3FileService;

    @Override
    public FormFile getAndRefresh(Long id) {
        FormFile formFile = getById(id);
        if (formFile == null) {
            return null;
        }
        S3File s3File = s3FileService.getById(formFile.getFileId());
        if (s3File == null) {
            return null;
        }
        formFile.setUrl(s3File.signedUrl());
        formFile.setExpires(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 720);
        return formFile;
    }

    @Override
    @SneakyThrows
    public FormFile uploadFile(MultipartFile file, String fileType, Integer workspaceId) {
        Integer id= s3FileService.safes3_fileGetNextId();
        Integer   formFileId=  safeformFileGetNextId();
//        UUID uuid = UUID.randomUUID();
        S3File s3File = AmazonUtil.uploadFile(file, FILE_BUCKET,id);
        s3FileService.save(s3File);
        FormFile formFile = new FormFile();
        formFile.setId(formFileId);
        formFile.setFileName(file.getOriginalFilename());
        formFile.setFileId(id);
        formFile.setFileType(fileType);
        formFile.setUrl(s3File.signedUrl());
        formFile.setExpires(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 720);
        save(formFile);
        return formFile;
    }

    @Transactional
    public Integer safeformFileGetNextId() {
        QueryWrapper<FormFile> wrapper = new QueryWrapper<>();
        wrapper.select("MAX(id)");

        Integer maxId = getBaseMapper().selectObjs(wrapper).stream()
                .filter(java.util.Objects::nonNull)
                .map(value -> ((Number) value).intValue())
                .findFirst()
                .orElse(0);

        return maxId + 1;
    }

}




