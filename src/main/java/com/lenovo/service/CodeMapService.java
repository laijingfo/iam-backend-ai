package com.lenovo.service;

import com.lenovo.entity.CodeMap;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

/**
 * @author mercury
 * @description 针对表【code_map】的数据库操作Service
 * @createDate 2024-09-02 15:22:52
 */
public interface CodeMapService extends IService<CodeMap> {

    Map<Integer, String> getMap(String type, String lang);
}
