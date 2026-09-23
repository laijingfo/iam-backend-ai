package com.lenovo.controller;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lenovo.bean.knowledge.*;
import com.lenovo.ai.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author laifo
 * @version 1.0
 * @date 2026-09-17 09:20
 * @project iam-backend
 * @description 知识库
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/knowledge")
@Slf4j
public class KnowledgeController extends BaseController {

    @Autowired
    private KnowledgeService knowledgeService;

    /**
     * 查询知识库答案
     */
    @PostMapping("/queryAnswer")
    public ResponseEntity queryAnswer(@RequestBody KnowledgeBase knowledgeBase) {
        try {
            JSONArray result = knowledgeService.queryAnswer(knowledgeBase);
            return ok(result);
        } catch (Exception e) {
            log.error("Failed to parse json data. json: {}", knowledgeBase, e);
            throw new RuntimeException("Failed to parse json data.");
        }
    }

    /**
     * 查询指定用户下的所有工作区
     */
    @GetMapping("/queryProject")
    public ResponseEntity queryProject(@RequestBody ProjectBase projectBase) {
        try {
            List<ProjectBase> result = knowledgeService.queryProject(projectBase);
            return ok(result);
        } catch (Exception e) {
            log.error("Failed to parse json data. json: {}", projectBase, e);
            throw new RuntimeException("Failed to parse json data.");
        }
    }

    /**
     * 查询工作区下的所有知识库
     */
    @GetMapping("/queryKnowledgeBase")
    public ResponseEntity queryKnowledgeBase(@RequestBody ProjectBase projectBase) {
        try {
            List<Long> result = knowledgeService.queryKnowledgeBase(projectBase);
            return ok(result);
        } catch (Exception e) {
            log.error("Failed to parse json data. json: {}", projectBase, e);
            throw new RuntimeException("Failed to parse json data.");
        }
    }

    /**
     * 获取知识库下的所有文档
     */
    @PostMapping("/getDocuments")
    public ResponseEntity getDocuments(String knowledgeBaseId) {
        JSONObject jsonResponse = knowledgeService.getDocuments(knowledgeBaseId);
        return ok(jsonResponse);
    }


    /**
     * 分页获取 chunk 文档 数据
     */
    @PostMapping("/getPageChunks")
    public ResponseEntity getPageChunks(Chunks  chunks) {
        JSONObject jsonResponse = knowledgeService.getPageChunks(chunks);
        return ok(jsonResponse);
    }

    @PostMapping("/knowledgeUpload")
    public ResponseEntity knowledgeUpload(@RequestBody KnowledgeUpload knowledgeUpload) {
        try {
            String result = knowledgeService.uploadKnowledge(knowledgeUpload);
            return ok(result);
        } catch (Exception e) {
            log.error("Failed to parse json data. json: {}", knowledgeUpload, e);
            throw new RuntimeException("Failed to parse json data.");
        }
    }



}
