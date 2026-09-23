package com.lenovo.controller;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lenovo.config.LogOperation;
import com.lenovo.mapper.ItsApplicationDataMapper;
import com.lenovo.mapper.LenovoUserMapper;
import com.lenovo.util.JsonToMapConverter;
import com.lenovo.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 字典 控制器
 */

@Slf4j
@RestController
@RequestMapping("/dict")
@RequiredArgsConstructor
public class DictController {
    // 字典直接查库或缓存
    private final RedisUtils redisUtils;
    private final LenovoUserMapper lenovoUserMapper;
    private final ItsApplicationDataMapper itsApplicationDataMapper;


    /* ======= redis中字典前缀 ======= */
    private static final String DICT_PREFIX = "dict:";
    private static final String DEPT_KEY = DICT_PREFIX + "dept";    // 部门
    private static final String OPS_DOMAIN_KEY = DICT_PREFIX + "opsDomain";
    private static final String OPS_TOWER_KEY = DICT_PREFIX + "opsTower";
    private static final String OPS_OWNER_KEY = DICT_PREFIX + "opsOwner";

    /* ====== 1. 列名映射 ====== */
    private static final Map<String, String> COL_MAP = Map.of(
            OPS_DOMAIN_KEY, "operation_owner_domain",
            OPS_TOWER_KEY,  "operation_owner_tower",
            OPS_OWNER_KEY,  "operation_owner"
    );
    /* ======= 公共方法抽取 ======= */
    private List<Map<String, String>> queryDict(String cacheKey,
                                                Supplier<List<Map<String, String>>> dbLoader) {
        try {
            if (redisUtils.hasKey(cacheKey)) {
                log.info("从缓存中获取{}", cacheKey);
                String json = (String) redisUtils.get(cacheKey);
                return JSON.parseObject(json, new TypeReference<List<Map<String, String>>>() {});
            }
        } catch (Exception e) {
            log.error("缓存读取失败[{}]：{}", cacheKey, e.getMessage());
        }
        // 查库格式统一
        List<Map<String, String>> data = dbLoader.get();
        String jsonString = JSON.toJSONString(data,
                SerializerFeature.WriteMapNullValue,
                SerializerFeature.WriteNullListAsEmpty,
                SerializerFeature.DisableCircularReferenceDetect);
        redisUtils.set(cacheKey, jsonString);
        return data;
    }

    /**
     * 部门信息
     */

    @GetMapping("/dept")
    @LogOperation(module = "字典-部门信息", type = LogOperation.OperationType.QUERY, value = "查询部门信息")
    public List<Map<String,String>> dept() {
        return queryDict(DEPT_KEY, lenovoUserMapper::getDept);
    }

    /**
     * 运维负责人Domain
     */
    @GetMapping("/opsDomain")
    @LogOperation(module = "字典-运维负责人Domain", type = LogOperation.OperationType.QUERY, value = "查询运维负责人Domain")
    public List<Map<String,String>> opsDomain() {
        return queryDict(OPS_DOMAIN_KEY,
                () -> itsApplicationDataMapper.getDict(COL_MAP.get(OPS_DOMAIN_KEY))
        );
    }

    /**
     * 运维负责人Tower
     */
    @GetMapping("/opsTower")
    @LogOperation(module = "字典-运维负责人Tower", type = LogOperation.OperationType.QUERY, value = "查询运维负责人Tower")
    public List<Map<String,String>> opsTower() {
        return queryDict(OPS_TOWER_KEY,
                () -> itsApplicationDataMapper.getDict(COL_MAP.get(OPS_TOWER_KEY))
        );
    }


    /**
     * 运维负责人
     */
    @GetMapping("/opsOwner")
    @LogOperation(module = "字典-运维负责人", type = LogOperation.OperationType.QUERY, value = "查询运维负责人")
    public List<Map<String,String>> opsOwner() {
        return queryDict(OPS_OWNER_KEY,
                () -> itsApplicationDataMapper.getDict(COL_MAP.get(OPS_OWNER_KEY))
        );
    }


}
