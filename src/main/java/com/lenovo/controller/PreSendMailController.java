package com.lenovo.controller;

import com.lenovo.dto.MailSendRequest;
import com.lenovo.service.PreSendMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/preSendMail")
public class PreSendMailController {
    final PreSendMailService preSendMailService;

    /**
     * 生成lm预发送清单
     */
    @RequestMapping("/generateLmList")
    public ResponseEntity generateLmPreSendList(
            @RequestBody MailSendRequest request
    ) {
        preSendMailService.generateLmPreSendList(request);
        return ResponseEntity.ok("success");
    }

    /**
     * 生成用户权限移除预发送清单
     */
    @RequestMapping("/generateUserWillRemoveList")
    public ResponseEntity generateUserWillRemoveList(
            @RequestBody MailSendRequest request
    ) {
        Integer count = preSendMailService.generateUserWillRemoveList();
        return ResponseEntity.ok("success 写入："+count);
    }

    /**
     * 生成bpo预发送清单
     */
    @RequestMapping("/generateBpoList")
    public ResponseEntity generateBpoPreSendList(
            @RequestBody MailSendRequest request
    ) {
        preSendMailService.generateBpoPreSendList(request);
        return ResponseEntity.ok("success");
    }

    /**
     * 催办用户预发送清单
     */
    @RequestMapping("/generateReminderUserList")
    public ResponseEntity generateReminderUserPreSendList() {
        preSendMailService.generateReminderUserPreSendList();
        return ResponseEntity.ok("success");
    }

    /**
     * 最终结果有移除的用户预发送清单
     *
     */
    @RequestMapping("/generateFinalRemoveUserPreSendList")
    public ResponseEntity generateFinalRemoveUserPreSendList() {
        preSendMailService.generateFinalRemoveUserPreSendList();
        return ResponseEntity.ok("success");
    }

}
