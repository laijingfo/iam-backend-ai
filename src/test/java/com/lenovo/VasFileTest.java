package com.lenovo;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lenovo.bean.apihub.ApiHubTokenBean;
import com.lenovo.bean.knowledge.KnowledgeBase;
import com.lenovo.bean.knowledge.KnowledgeRelation;
import com.lenovo.service.ItsApplicationAccessDataService;
import com.lenovo.ai.KnowledgeService;
import com.lenovo.service.SyncItsApplicationService;
import com.lenovo.util.ApiHubUtils;
import com.lenovo.util.HttpUtilsSkpSsl;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest
@RunWith(SpringRunner.class)
@Slf4j
public class VasFileTest {
    @Value("${api-hub.domain}")
    private String domain;

    @Value("${api-hub.username}")
    private String username;

    @Value("${api-hub.password}")
    private String password;

    @Value("${api-hub.x-api-key}")
    private String xApiKey;

    @Value("${api-hub.km-verse-public-path}")
    private String kmversePublicPath;

    @Value("${api-hub.x-project-id}")
    private String xProjectId;

    @Value("${api-hub.x-user-token}")
    private String xUserToken;

    static String ACCESS_TOKEN = "api_hub_km:access_token";
    static String REFRESH_TOKEN = "api_hub_km:refresh_token";

    @Autowired
    private SyncItsApplicationService syncItsApplicationService;
    @Autowired
    private ItsApplicationAccessDataService splunkDataSourceService;
    @Autowired
    private ApiHubUtils apiHubUtils;

    @Test
    public void syncNaData() {
        splunkDataSourceService.syncFullData(0);
    }

    @Test
    public void syncSplunkApplicationData() {
        syncItsApplicationService.syncSplunkApplicationData();
    }

//    /tpass/kmverse/outerapi/v1/knowledge/retrieval
    @Test
    public void queryKnowledge() {
        String path = domain + kmversePublicPath + "/knowledgeBase/"+xProjectId+"/knowledgeBases";
        HashMap<String, String> header = buildHeader();
        HashMap<String, Object> body = new HashMap<>();
        body.put("itCode", "laijf2");
        String httpPost = HttpUtilsSkpSsl.getHttpGet(path, header, body);
        System.out.println(httpPost);
    }

    @Test
    public void queryProject() {
        String path = domain + kmversePublicPath + "/project";
        HashMap<String, String> header = buildHeader();
        Map<String, Object> map = new HashMap<>();
        map.put("itCode", "laijf2");
        String httpPost = HttpUtilsSkpSsl.getHttpGet(path, header, map);
        System.out.println(httpPost);
    }
    @Autowired
    private KnowledgeService knowledgeService;

    @Test
    public void queryAnswer1() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        List<KnowledgeRelation> relationList = new ArrayList<>();
        KnowledgeRelation knowledgeRelation = new KnowledgeRelation();
        knowledgeRelation.setKnowledgeBaseId(737245476834245L);
        knowledgeRelation.setFilter("2222");
        relationList.add(knowledgeRelation);
        knowledgeBase.setRelation(relationList);
        knowledgeBase.setSimilarityTopK(3);
        knowledgeBase.setQuery("为什么需要做权限审核");
        knowledgeBase.setIndexMode("keyword");
        knowledgeBase.setProjectId(79);
        JSONArray result = knowledgeService.queryAnswer(knowledgeBase);
        System.out.println(result);
    }
    @Test
    public void queryAnswer() {
        String path = domain + kmversePublicPath + "/knowledge/retrieval";
        HashMap<String, String> header = buildHeader();

        HashMap<String, Object> body = new HashMap<>();
        List<HashMap<String, Object>> relationList = new ArrayList<>();
        HashMap<String, Object> relationMap = new HashMap<>();
        relationMap.put("knowledgeBaseId", 737245476834245L);
//        relationMap.put("knowledgeBaseId", 737219370003397L);
        relationMap.put("docIds", List.of());   //指定文档id
        relationMap.put("filter", "2222");           //筛选条件
        relationList.add(relationMap);
        body.put("projectId", xProjectId);
        body.put("relation", relationList);
        body.put("query", "为什么需要做权限审核");      //检索内容
        body.put("indexMode", "keyword");     //检索方式 vector或keyword
        body.put("similarityTopK", 3);       //similarityTopK 前几

        String jsonBody = JSON.toJSONString(body);

        String httpPost = HttpUtilsSkpSsl.getHttpPost(path, header, jsonBody);

        // 解析返回结果
        try {
            JSONObject jsonResponse = JSON.parseObject(httpPost);
            if (jsonResponse.getIntValue("code") == 200) {
                JSONArray result = jsonResponse.getJSONArray("result");
                System.out.println(result.toJSONString() + "+++++++++++++++++++++");
            } else {
                throw new RuntimeException("Failed to get json data: " + jsonResponse.getString("message"));
            }
        } catch (Exception e) {
            log.error("Failed to parse json data. json: {}", httpPost, e);
            throw new RuntimeException("Failed to parse json data.");
        }
    }

    @Test
    public void queryDocuments() {
        String path = domain + kmversePublicPath + "/knowledgeBase/" + 729823680010117L + "/documents";

        HashMap<String, String> header = buildHeader();
        String httpGet = HttpUtilsSkpSsl.getHttpGet(path, header);
        JSONObject jsonResponse = JSON.parseObject(httpGet);
        if (jsonResponse.getIntValue("code") == 200) {
            JSONArray result = jsonResponse.getJSONArray("result");
            System.out.println(result.toJSONString() + "====");
        }
    }


    @Test
    public void knowledgeUpload() {
        String path = domain + kmversePublicPath + "/knowledge/upload";

        HashMap<String, String> header = buildHeader();
        HashMap<String, Object> body = new HashMap<>();
        body.put("knowledgeBaseId",737245476834245L);
        body.put("projectId", xProjectId);
        body.put("file", "C:\\Users\\laiji\\Desktop\\kafka.txt");
        body.put("fileType", "txt");
        String jsonBody = JSON.toJSONString(body);
        String httpGet = HttpUtilsSkpSsl.getHttpPost(path, header, jsonBody);
        JSONObject jsonResponse = JSON.parseObject(httpGet);
        System.out.println(jsonResponse);
    }

    @Test
    public void deleteDocuments() {

        String path = domain + kmversePublicPath + "/document/batch";
        HashMap<String, String> header = buildHeader();
        // 1. 构造请求头

        // 2. 构造请求体
        List<Long> docIdList = new ArrayList<>();
        docIdList.add(730874519830341L);

        // 3. 创建忽略SSL验证的RestTemplate
        RestTemplate restTemplate = createIgnoreSSLRestTemplate();

        // 4. 构造请求
        HttpHeaders headers = new HttpHeaders();
        headers.setAll(header);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<Long>> requestEntity = new HttpEntity<>(docIdList, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    path,
                    HttpMethod.DELETE,
                    requestEntity,
                    String.class
            );
            System.out.println("响应状态码: " + response.getStatusCode());
            System.out.println("响应内容: " + response.getBody());
        } catch (RestClientException e) {
            System.err.println("请求失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 创建忽略SSL证书验证的RestTemplate（仅用于测试环境）
     */
    private RestTemplate createIgnoreSSLRestTemplate() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

            return new RestTemplate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL-ignoring RestTemplate", e);
        }
    }



    @Test
    public void pageChunks() {
        JSONObject jsonResponse = knowledgeService.getDocuments("737245476834245");
        System.out.println(jsonResponse);
    }

    @Test
    public void documents() {
        String path = domain + kmversePublicPath + "/knowledgeBase/737245476834245/documents";
        HashMap<String, String> header = buildHeader();
        String httpGet = HttpUtilsSkpSsl.getHttpGet(path, header);
        JSONObject jsonResponse = JSON.parseObject(httpGet);
        System.out.println(jsonResponse);
    }

    /**
     * 构建公共请求头
     */
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

    @Test
    public void setDomain() throws Exception {
        var client = java.net.http.HttpClient.newHttpClient();
        var req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://dashscope.aliyuncs.com"))
                .GET().build();
        var resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        System.out.println(resp.statusCode());
    }
}

