package com.lenovo.bean;

import java.util.LinkedHashMap;
import java.util.Map;

public class RequestLogMessage {

    private String method;
    private String url;
    private Map<String, String> requestHeaders = new LinkedHashMap<>();
    private Map<String, String> params = new LinkedHashMap<>();
    private String requestEncoding;
    private String requestContentType;
    private String requestBody;
    private int responseStatus;
    private Map<String, String> responseHeaders = new LinkedHashMap<>();
    private String responseEncoding;
    private String responseContentType;
    private String responseBody;
    private String operationName;
    private String operationType;

    public void setMethod(String method) {
        this.method = method;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setRequestEncoding(String requestEncoding) {
        this.requestEncoding = requestEncoding;
    }

    public void setRequestContentType(String requestContentType) {
        this.requestContentType = requestContentType;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public void setResponseStatus(int responseStatus) {
        this.responseStatus = responseStatus;
    }

    public void setResponseEncoding(String responseEncoding) {
        this.responseEncoding = responseEncoding;
    }

    public void setResponseContentType(String responseContentType) {
        this.responseContentType = responseContentType;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }
    public void addRequestHeader(String key, String value) {
        requestHeaders.put(key, value);
    }

    public void addResponseHeader(String key, String value) {
        responseHeaders.put(key, value);
    }

    public void addParam(String key, String value) {
        params.put(key, value);
    }


    private String getString() {
        return "\n*****INCOMING REQUEST BEGIN*****\n" +
                " Operation: " + operationName + " / " + operationType + "\n" +
                " Method: " + method + "\n" +
                " Url: " + url + "\n" +
                " Encoding: " + requestEncoding + "\n" +
                " Content type: " + requestContentType + "\n" +
                " Headers: " + requestHeaders + "\n" +
                " Parameters: " + params + "\n" +
                " Body: " + requestBody + "\n" +
                "*****REQUEST END*****\n" +
                "*****RESPONSE BEGIN*****\n" +
                " Encoding: " + responseEncoding + "\n" +
                " Content type: " + responseContentType + "\n" +
                " Status: " + responseStatus + "\n" +
                " Headers: " + responseHeaders + "\n" +
//                " Body: " + responseBody + "\n" +
                "*****RESPONSE END*****";
    }

    @Override
    public String toString() {
        return getString();
    }

}
