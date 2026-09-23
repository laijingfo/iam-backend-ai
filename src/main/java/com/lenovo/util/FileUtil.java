package com.lenovo.util;

import org.springframework.web.multipart.MultipartFile;

public class FileUtil {

    /**
     * 获取上传的文件名称，不同浏览器file.getOriginalFilename()获取的文件名不一样，有可能包含路径
     * 在IE浏览器中上传文件，通过MultipartFile的getOriginalFilename实际上与Chrome浏览器的返回结果不同
     * 在Chrome浏览器下，此方法是直接返回“XXX.xlsx”结果的。
     * 而在IE浏览器环境下，此方法是返回带盘符信息的“C:/XXX.xlsx”
     * @param file
     * @return
     */
    public static String getFileName(MultipartFile file) {
        //获取文件名称（可能包含路径）
        String fileName = file.getOriginalFilename();
        //获取最后"/"的索引
        int startIndex = fileName.replaceAll("\\\\", "/").lastIndexOf("/");
        //截取文件名，根据业务，文件后缀也可去掉，此处去除后缀
//        fileName = fileName.substring(startIndex + 1).substring(0, fileName.indexOf("."));
        //带文件后缀
        fileName = fileName.substring(startIndex + 1);
        return fileName;
    }
}
