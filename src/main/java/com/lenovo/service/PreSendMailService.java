package com.lenovo.service;

import com.lenovo.bean.PreSendBean;
import com.lenovo.dto.MailSendRequest;
import com.lenovo.entity.AllowSendEmailForBpo;

import java.util.List;

public interface PreSendMailService {
    // lm通知相关
    void generateLmPreSendList(MailSendRequest request);

    Long getPreSendLmCount();

    List<PreSendBean> getPreSendLmList();

    void removePreSendLmById(Long id);

    // user remove通知相关(role移除通知 全量)
    Integer generateUserWillRemoveList();

    Long getPreSendUserWillRemoveCount();

    List<PreSendBean> getPreSendUserWillRemoveList();

    void removePreSendUserWillRemoveById(Long id);

    // bpo通知相关
    void generateBpoPreSendList(MailSendRequest request);

    Long getPreSendBpoCount();

    List<PreSendBean> getPreSendBpoList();

    void removePreSendBpoById(Long id);

    //  user reminder 通知相关(给用户的催办 全量)
    Integer generateReminderUserPreSendList();

    Long getPreSendReminderUserCount();

    List<PreSendBean> getPreSendReminderUserList();

    void removePreSendReminderUserById(Long id);

    // final remove 最终审核移除(给用户移除的全量)
    Integer generateFinalRemoveUserPreSendList();

    Long getPreSendFinalRemoveUserCount();

    List<PreSendBean> getPreSendFinalRemoveUserList();

    void removePreSendFinalRemoveUserById(Long id);

}
