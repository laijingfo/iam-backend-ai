package com.lenovo.service.impl;

import com.lenovo.bean.PreSendBean;
import com.lenovo.dto.MailSendRequest;
import com.lenovo.mapper.PreSendMailMapper;
import com.lenovo.service.PreSendMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreSendMailServiceImpl implements PreSendMailService {

    private final PreSendMailMapper preSendMailMapper;


    @Override
    @Transactional
    public void generateLmPreSendList(MailSendRequest request) {
        preSendMailMapper.clearLmPreSendList();
        Integer lmPreSend = preSendMailMapper.buildLmPreSendList(request);
        Integer lmDelegate = preSendMailMapper.buildLmDelegateList();
    }

    @Override
    public Long getPreSendLmCount() {
        return preSendMailMapper.getPreSendLmCount();
    }

    @Override
    public List<PreSendBean> getPreSendLmList() {
        return preSendMailMapper.getPreSendLmList();
    }

    @Override
    public void removePreSendLmById(Long id) {
        preSendMailMapper.removePreSendLmById(id);
    }

    @Override
    @Transactional
    public Integer generateUserWillRemoveList() {
        preSendMailMapper.clearUserWillRemoveList();
        Integer userRemove = preSendMailMapper.buildUserWillRemoveList();
        return userRemove;
    }

    @Override
    public Long getPreSendUserWillRemoveCount() {
        return preSendMailMapper.getPreSendUserWillRemoveCount();
    }

    @Override
    public List<PreSendBean> getPreSendUserWillRemoveList() {
        return preSendMailMapper.getPreSendUserWillRemoveList();
    }

    @Override
    public void removePreSendUserWillRemoveById(Long id) {
        preSendMailMapper.removePreSendUserWillRemoveById(id);
    }

    @Override
    @Transactional
    public void generateBpoPreSendList(MailSendRequest request) {
        preSendMailMapper.clearBPOPreList();
        Integer bpoPreSend = preSendMailMapper.buildBPOPreList(request);
        Integer bpoDelegate = preSendMailMapper.buildBpoDelegateList();

    }

    @Override
    public Long getPreSendBpoCount() {
        return preSendMailMapper.getPreSendBpoCount();
    }

    @Override
    public List<PreSendBean> getPreSendBpoList() {

        return preSendMailMapper.getPreSendBpoList();
    }

    @Override
    public void removePreSendBpoById(Long id) {
        preSendMailMapper.removePreSendBpoById(id);
    }

    @Override
    @Transactional
    public Integer generateReminderUserPreSendList() {
        preSendMailMapper.clearReminderUserPreSendList();
        return preSendMailMapper.buildReminderUserPreSendList();
    }

    @Override
    public Long getPreSendReminderUserCount() {
        return preSendMailMapper.getPreSendReminderUserCount();
    }

    @Override
    public List<PreSendBean> getPreSendReminderUserList() {
        return preSendMailMapper.getPreSendReminderUserList();
    }

    @Override
    public void removePreSendReminderUserById(Long id) {
        preSendMailMapper.removePreSendReminderUserById(id);
    }

    @Override
    @Transactional
    public Integer generateFinalRemoveUserPreSendList() {
        preSendMailMapper.clearFinalRemoveUserPreSendList();
        return preSendMailMapper.buildFinalRemoveUserPreSendList();
    }

    @Override
    public Long getPreSendFinalRemoveUserCount() {
        return preSendMailMapper.getPreSendFinalRemoveUserCount();
    }

    @Override
    public List<PreSendBean> getPreSendFinalRemoveUserList() {
        return preSendMailMapper.getPreSendFinalRemoveUserList();
    }

    @Override
    public void removePreSendFinalRemoveUserById(Long id) {
        preSendMailMapper.removePreSendFinalRemoveUserById(id);
    }

}
