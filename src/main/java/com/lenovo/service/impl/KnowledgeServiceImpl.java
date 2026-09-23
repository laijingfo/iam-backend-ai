package com.lenovo.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lenovo.bean.apihub.ApiHubTokenBean;
import com.lenovo.bean.knowledge.Chunks;
import com.lenovo.bean.knowledge.KnowledgeBase;
import com.lenovo.bean.knowledge.KnowledgeUpload;
import com.lenovo.bean.knowledge.ProjectBase;
import com.lenovo.ai.KnowledgeService;
import com.lenovo.util.ApiHubUtils;
import com.lenovo.util.HttpUtilsSkpSsl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-17 09:25
 * @project iam-backend
 * @description 知识库
 */
@Slf4j
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    @Value("${api-hub.domain}")
    private String domain;

    @Value("${api-hub.km-verse-public-path}")
    private String kmversePublicPath;

    @Value("${api-hub.x-project-id}")
    private String xProjectId;

    private final ApiHubUtils apiHubUtils;

    @Value("${api-hub.username}")
    private String username;

    @Value("${api-hub.password}")
    private String password;

    @Value("${api-hub.x-api-key}")
    private String xApiKey;

    @Value("${api-hub.x-user-token}")
    private String xUserToken;

    static String ACCESS_TOKEN = "api_hub_km:access_token";

    static String REFRESH_TOKEN = "api_hub_km:refresh_token";

    public KnowledgeServiceImpl(ApiHubUtils apiHubUtils) {
        this.apiHubUtils = apiHubUtils;
    }

    /**
     * 查询知识库答案
     */
    @Override
        public JSONArray queryAnswer(KnowledgeBase knowledgeBase) {
        String path = domain + kmversePublicPath + "/knowledge/retrieval";
        HashMap<String, String> header = buildHeader();
        String jsonBody = JSON.toJSONString(knowledgeBase);
        String httpPost = HttpUtilsSkpSsl.getHttpPost(path, header, jsonBody);
        // 解析返回结果
        try {
            JSONObject jsonResponse = JSON.parseObject(httpPost);
            if (jsonResponse.getIntValue("code") == 200) {
                return jsonResponse.getJSONArray("result");
            } else {
                throw new RuntimeException("Failed to get json data: " + jsonResponse.getString("message"));
            }
        } catch (Exception e) {
            log.error("Failed to parse json data. json: {}", httpPost, e);
            throw new RuntimeException("Failed to parse json data.");
        }
    }

    /**
     * 查询指定用户下的所有工作区
     */
    @Override
    public List<ProjectBase> queryProject(ProjectBase projectBase) {
        String path = domain + kmversePublicPath + "/project";
        HashMap<String, String> header = buildHeader();
        Map<String, Object> map = BeanUtil.beanToMap(projectBase, new HashMap<>(),
                false, true); // isToUnderlineCase=false, ignoreNullValue=true
        String httpGet = HttpUtilsSkpSsl.getHttpGet(path, header, map);
        JSONArray arr = parseArray(httpGet);

        List<ProjectBase> projectBaseList = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            JSONArray records = o.getJSONArray("records");
            for (int j = 0; j < records.size(); j++) {
                ProjectBase newProjectBase = new ProjectBase();
                JSONObject record = records.getJSONObject(j);
                newProjectBase.setId(record.getInteger("id"));
                newProjectBase.setName(record.getString("name"));
                newProjectBase.setDescription(record.getString("description"));
                newProjectBase.setBucket(record.getString("bucket"));
                newProjectBase.setCreatedBy(record.getString("createdBy"));
                newProjectBase.setLlmStatus(record.getString("llmStatus"));
                projectBaseList.add(newProjectBase);
            }
//            sb.append("- ID: ").append(o.getString("projectId"))
//                    .append(", 名称: ").append(o.getString("projectName"))
//                    .append("\n");
        }
        return  projectBaseList;
    }

    /**
     * 查询工作区下的所有知识库
     */
    @Override
    public List<Long>  queryKnowledgeBase(ProjectBase projectBase) {
        String path = domain + kmversePublicPath + "/knowledgeBase/"+projectBase.getProjectId()+"/knowledgeBases";
        HashMap<String, String> header = buildHeader();
        Map<String, Object> map = BeanUtil.beanToMap(projectBase, new HashMap<>(),
                false, true);
        String httpGet = HttpUtilsSkpSsl.getHttpGet(path, header, map);
        JSONObject jsonResponse = JSON.parseObject(httpGet);
        JSONArray result = jsonResponse.getJSONArray("result");
        List<Long> knowledgeBaseIds = new ArrayList<>();
        for (int i = 0; i < result.size(); i++) {
            JSONObject item = result.getJSONObject(i);
            JSONArray rows = item.getJSONArray("rows");
            for (int j = 0; j < rows.size(); j++) {
                JSONObject row = rows.getJSONObject(j);
                Long id = row.getLong("id");
                knowledgeBaseIds.add(id);
            }
        }
        return knowledgeBaseIds;
    }

    @Override
    public String uploadKnowledge(KnowledgeUpload knowledgeUpload) {
        String path = domain + kmversePublicPath + "/knowledge/upload";
        HashMap<String, String> header = buildHeader();
        String jsonBody = JSON.toJSONString(knowledgeUpload);
        String httpGet = HttpUtilsSkpSsl.getHttpPost(path, header, jsonBody);
        JSONObject jsonResponse = JSON.parseObject(httpGet);
        return jsonResponse.getString("message");
    }

    /**
     * 分页获取 chunk 文档 数据
     */
    @Override
    public JSONObject getPageChunks(Chunks chunks) {
        String path = domain + kmversePublicPath + "/document/pageChunks";
        HashMap<String, String> header = buildHeader();
        Map<String, Object> map = BeanUtil.beanToMap(chunks, new HashMap<>(),
                false, true);
        String httpGet = HttpUtilsSkpSsl.getHttpGet(path, header, map);
        return JSON.parseObject(httpGet);
    }

    /**
     * 获取知识库下的所有文档
     */
    @Override
    public JSONObject getDocuments(String knowledgeBaseId) {
        String path = domain + kmversePublicPath + "/knowledgeBase/"+knowledgeBaseId+"/documents";
        HashMap<String, String> header = buildHeader();
        String httpGet = HttpUtilsSkpSsl.getHttpGet(path, header);
        return JSON.parseObject(httpGet);
    }

//    @Override
//    public KnowledgeBase getKnowledgeBaseById(Long knowledgeBaseId) {
//        return null;
//    }

    private JSONArray parseArray(String resp) {
        if (resp == null || resp.isBlank()) return new JSONArray();
        try {
            JSONObject json = JSONObject.parseObject(resp);
            JSONArray arr = json.getJSONArray("data");
            if (arr == null) arr = json.getJSONArray("result");
            return arr != null ? arr : new JSONArray();
        } catch (Exception e) {
            log.warn("解析响应失败: {}", resp, e);
            return new JSONArray();
        }
    }

    private HashMap<String, String> buildHeader() {
        ApiHubTokenBean tokenBean = apiHubUtils.getValidToken(username, password, domain, xApiKey, ACCESS_TOKEN, REFRESH_TOKEN);
        if (tokenBean == null) {
            throw new RuntimeException("Failed to get valid token.");
        }

        HashMap<String, String> header = new HashMap<>();
        header.put("X-API-KEY", xApiKey);
        header.put("Authorization", tokenBean.getAccessToken());
        header.put("X-Project-Id", xProjectId);
        header.put("X-User-Token", xUserToken);
        return header;
    }
}
