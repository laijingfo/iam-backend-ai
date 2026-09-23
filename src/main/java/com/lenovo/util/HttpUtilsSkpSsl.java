package com.lenovo.util;

import com.alibaba.fastjson.JSONArray;
import com.lenovo.entity.HttpGetWithEntity;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;


public class HttpUtilsSkpSsl {



    /*
     * @Description:跳过ssl认证，
     * @Params: * @Param uri:
     * @Param headerMap:
     * @Param paramMap:
     * @Return java.lang.String
     */

    public static String getHttpPost(String uri, Map<String, String> headerMap, Map<String, String> paramMap) {

        HttpClient httpClient = wrapClient();//创建HttpClient,关键点一
        //uri,这里决定用 post,还是用其他方式
        HttpPost httpPost = new HttpPost(uri);
        //头
        for (Map.Entry<String, String> entry : headerMap.entrySet()) {
            httpPost.addHeader(entry.getKey(), entry.getValue());
        }


        StringBuilder paramsStr = new StringBuilder();
        paramMap.forEach((key, value) -> {
            try {
                paramsStr.append(URLEncoder.encode(key, "UTF-8")).append("=").append(URLEncoder.encode(String.valueOf(value), "UTF-8")).append("&");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        });


        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
        httpPost.setEntity(new StringEntity(paramsStr.substring(0, paramsStr.length() - 1), StandardCharsets.UTF_8));

        //体
        /*   httpPost.setEntity(new StringEntity(JSONObject.toJSONString(paramMap), ContentType.create( "utf-8")));*/
        System.setProperty("jsse.enableSNIExtension", "false");//ssl免检  关键点二
        //访问
        try {
            HttpResponse response = httpClient.execute(httpPost);

            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = response.getEntity() == null
                    ? null
                    : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (statusCode < 200 || statusCode >= 300) {
                throw new IllegalStateException(
                        "Request failed, HTTP status code=" + statusCode
                                + ", response=" + responseBody
                );
            }

            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException(
                        "Request successful, but response content is empty, HTTP status code=" + statusCode
                );
            }

            return responseBody;
        } catch (IOException e) {
            throw new IllegalStateException("Request failed, uri=" + uri, e);
        }
    }




    public static HttpClient wrapClient() {//关键点三
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            X509TrustManager tm = new X509TrustManager() {
                @Override
                public void checkClientTrusted(
                        X509Certificate[] x509Certificates, String s) {
                }

                @Override
                public void checkServerTrusted(
                        X509Certificate[] x509Certificates, String s) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            };
            sc.init(null, new TrustManager[] {tm}, null);
            SSLConnectionSocketFactory ssf = new SSLConnectionSocketFactory(sc, NoopHostnameVerifier.INSTANCE);
            return HttpClients.custom().setSSLSocketFactory(ssf).build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            return HttpClients.createDefault();
        }
    }



    /**
     * 发送get请求，参数为json
     * @param url
     * @param param
     * @param encoding
     * @return
     * @throws Exception
     */
    public static String sendJsonByGetReq(String url, String param, String encoding) throws Exception {
        String body = "";
        //创建httpclient对象
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGetWithEntity httpGetWithEntity = new HttpGetWithEntity(url);
        HttpEntity httpEntity = new StringEntity(param, ContentType.APPLICATION_JSON);
        httpGetWithEntity.setEntity(httpEntity);
        //执行请求操作，并拿到结果（同步阻塞）
        CloseableHttpResponse response = client.execute(httpGetWithEntity);
        //获取结果实体
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            //按指定编码转换结果实体为String类型
            body = EntityUtils.toString(entity, encoding);
        }
        //释放链接
        response.close();
        return body;
    }
    public static String getHttpPost(String uri, Map<String, String> headerMap, List<Map<String, String>> resultList) {

        HttpClient httpClient = wrapClient(); // 创建HttpClient
        HttpPost httpPost = new HttpPost(uri);

        // 设置请求头
        for (Map.Entry<String, String> entry : headerMap.entrySet()) {
            httpPost.addHeader(entry.getKey(), entry.getValue());
        }

        // 将List<Map>转换为JSON数组字符串
        String jsonBody = JSONArray.toJSONString(resultList);

        // 设置Content-Type为JSON，并指定编码
        httpPost.setHeader("Content-Type", "application/json;charset=utf-8");
        httpPost.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));

        System.setProperty("jsse.enableSNIExtension", "false"); // SSL设置

        String entity = null;
        try {
            HttpResponse httpResponse = httpClient.execute(httpPost);
            entity = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace(); // 处理异常
        }
        return entity;
    }

    /**
     *  post请求
     * @param uri
     * @param headerMap
     * @param jsonBody
     * @return
     */
    public static String getHttpPost(String uri, Map<String, String> headerMap, String jsonBody) {

        HttpClient httpClient = wrapClient(); // 创建HttpClient
        HttpPost httpPost = new HttpPost(uri);

        // 设置请求头
        for (Map.Entry<String, String> entry : headerMap.entrySet()) {
            httpPost.addHeader(entry.getKey(), entry.getValue());
        }

        // 设置Content-Type为JSON，并指定编码
        httpPost.setHeader("Content-Type", "application/json;charset=utf-8");
        httpPost.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));

        System.setProperty("jsse.enableSNIExtension", "false"); // SSL设置

        String entity = null;
        try {
            HttpResponse httpResponse = httpClient.execute(httpPost);
            entity = EntityUtils.toString(httpResponse.getEntity(), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace(); // 处理异常
        }
        return entity;
    }


    /**
     * 从 HttpServletRequest 中获取客户端真实 IP 地址
     * 支持代理服务器（Nginx、Apache 等）转发场景
     */
    public static String getIpAddr(HttpServletRequest request)
    {
        if (request == null)
        {
            return "unknown";
        }

        // 代理服务器转发时，会将真实 IP 放在以下请求头中（需代理配置转发）
        String[] ipHeaders =
                {
                        "X-Forwarded-For",    // 最常用，多层代理时可能包含多个 IP（逗号分隔，第一个为真实 IP）
                        "Proxy-Client-IP",    // Apache 代理
                        "WL-Proxy-Client-IP", // WebLogic 代理
                        "HTTP_CLIENT_IP",     // 部分代理
                        "HTTP_X_FORWARDED_FOR"// 部分代理
                };

        // 遍历代理头，提取非空且非 "unknown" 的 IP
        for (String header : ipHeaders)
        {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip))
            {
                // 多层代理时，X-Forwarded-For 格式为：真实IP,代理IP1,代理IP2...，取第一个
                if (ip.contains(","))
                {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }

        // 若未获取到代理头，直接获取远程地址（可能是代理服务器 IP）
        return request.getRemoteAddr();
    }
    /**
     * 发送GET请求，跳过SSL认证，支持查询参数拼接到URL后面
     * @param uri 请求地址
     * @param headerMap 请求头参数（可为null）
     * @param paramMap 查询参数（可为null，会拼接到URL后面）
     * @return 响应内容字符串
     */
    public static String getHttpGet(String uri, Map<String, String> headerMap, Map<String, Object> paramMap) {
        // 构建查询参数字符串
        StringBuilder paramsStr = new StringBuilder();
        if (paramMap != null && !paramMap.isEmpty()) {
            paramMap.forEach((key, value) -> {
                try {
                    paramsStr.append(URLEncoder.encode(key, "UTF-8"))
                            .append("=")
                            .append(URLEncoder.encode(String.valueOf(value), "UTF-8"))
                            .append("&");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            });
            // 删除最后一个 "&"
            if (paramsStr.length() > 0) {
                paramsStr.deleteCharAt(paramsStr.length() - 1);
            }
        }

        // 拼接查询参数到URI
        String fullUri = uri;
        if (paramsStr.length() > 0) {
            fullUri = uri.contains("?") ? uri + "&" + paramsStr : uri + "?" + paramsStr;
        }

        CloseableHttpClient httpClient = (CloseableHttpClient) wrapClient();
        HttpGetWithEntity httpGet = new HttpGetWithEntity(fullUri);

        // 设置请求头
        if (headerMap != null) {
            for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                httpGet.addHeader(entry.getKey(), entry.getValue());
            }
        }

        System.setProperty("jsse.enableSNIExtension", "false");

        try {
            CloseableHttpResponse response = httpClient.execute(httpGet);
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = response.getEntity() == null
                    ? null
                    : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (statusCode < 200 || statusCode >= 300) {
                throw new IllegalStateException(
                        "Request failed, HTTP status code=" + statusCode
                                + ", response=" + responseBody
                );
            }

            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException(
                        "Request successful, but response content is empty, HTTP status code=" + statusCode
                );
            }

            return responseBody;
        } catch (IOException e) {
            throw new IllegalStateException("Request failed, uri=" + fullUri, e);
        }
    }

    /**
     * 发送GET请求，跳过SSL认证，不带查询参数
     * @param uri 请求地址
     * @param headerMap 请求头参数（可为null）
     * @return 响应内容字符串
     */
    public static String getHttpGet(String uri, Map<String, String> headerMap) {
        return getHttpGet(uri, headerMap, null);
    }

}