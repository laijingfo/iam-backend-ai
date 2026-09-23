package com.lenovo.util;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import com.lenovo.constant.ApiParamsConstant;
import com.lenovo.security.utils.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SplunkSyncAdapter {
    @Value("${splunk.address}")
    public String splunkAddress;

    @Value("${splunk.authorization}")
    protected String cybertronSplunkToken;

    @Value("${tsi.authorization}")
    protected String tsiauthorization;

    @Value("${tsi.url}")
    protected String tsiUrl;


    public String makeRequest(String url, String search, String customSplunkToken, String execMode,Integer offset,Integer count) {
        HashMap<String, String> header = new HashMap<String, String>();
        header.put("Authorization", "Bearer " + (StringUtils.isEmpty(customSplunkToken) ? cybertronSplunkToken : customSplunkToken));
        header.put("Content-Type", "application/x-www-form-urlencoded");
        HashMap<String, String> body = new HashMap<String, String>();

        body.put("search", search);
        body.put("exec_mode", execMode);
        body.put("count", count.toString());
        body.put("offset", offset.toString());
        body.put("output_mode", "json");
        //post 请求 获得token
        return HttpUtilsSkpSsl.getHttpPost(url, header, body);
    }

    /**
     * 默认exec_mode 同步模式
     */
    public String makeRequest(String url, String search, Integer offset,Integer count) {
        return makeRequest(url, search, null, ApiParamsConstant.SYNC_EXEC_MODE ,offset, count);
    }

    /**
     * 指定exec_mode
     */
    public String makeRequest(String url, String search, String execMode, Integer offset,Integer count) {
        return makeRequest(url, search, null, execMode ,offset, count);
    }


    public <T> List<T> getResult(String jsonString, Class<T> classType) {
        if (jsonString == null || jsonString.isBlank()) {
            throw new IllegalArgumentException("Response cannot be empty");
        }

        JSONObject response = JSONObject.parseObject(jsonString);

        if (response.getJSONArray("results") == null) {
            throw new IllegalStateException("The response is missing the results field");
        }

        return response.getJSONArray("results")
                .toJavaList(classType, JSONReader.Feature.SupportSmartMatch);
    }
    /**
     * 同步数据TSI
     */
    public String sendToTSI(String json) {
        HashMap<String, String> header = new HashMap<String, String>();
        header.put("Authorization",tsiauthorization );
        header.put("Content-Type", "application/json");
        List<Map<String, String>> resultList = JsonToMapConverter.parseJsonArray(json);
        resultList.forEach(map -> {
            System.out.println("解析数组结果:");
            map.forEach((k, v) -> System.out.println(k + " : " + v));
        });
        return HttpUtilsSkpSsl.getHttpPost(tsiUrl, header, resultList);
    }


}
