package com.lenovo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.entity.CodeMap;
import com.lenovo.service.CodeMapService;
import com.lenovo.mapper.CodeMapMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author mercury
 * @description 针对表【code_map】的数据库操作Service实现
 * @createDate 2024-09-02 15:22:52
 */
@Service
public class CodeMapServiceImpl extends ServiceImpl<CodeMapMapper, CodeMap>
        implements CodeMapService {

    @Override
    public Map<Integer, String> getMap(String type, String lang) {
        QueryWrapper<CodeMap> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("type", type);
        List<CodeMap> list = list(queryWrapper);

        if (lang.equals("cn")) {
            return list.stream().collect(Collectors.toMap(CodeMap::getValue, CodeMap::getName));
        } else if (lang.equals("en")) {
            return list.stream().collect(Collectors.toMap(CodeMap::getValue, CodeMap::getEnName));
        }

        return Map.of();
    }
}



