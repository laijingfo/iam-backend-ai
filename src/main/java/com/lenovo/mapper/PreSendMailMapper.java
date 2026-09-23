package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lenovo.bean.PreSendBean;
import com.lenovo.dto.MailSendRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PreSendMailMapper extends BaseMapper {

    void clearLmPreSendList();

    Integer buildLmPreSendList(@Param("request") MailSendRequest request);

    Integer buildLmDelegateList();

    Long getPreSendLmCount();

    List<PreSendBean> getPreSendLmList();

    Integer removePreSendLmById(Long id);

    void clearUserWillRemoveList();

    Integer buildUserWillRemoveList();

    Long getPreSendUserWillRemoveCount();

    List<PreSendBean> getPreSendUserWillRemoveList();

    void removePreSendUserWillRemoveById(Long id);

    void clearBPOPreList();

    Integer buildBPOPreList(@Param("request") MailSendRequest request);

    Integer buildBpoDelegateList();

    Long getPreSendBpoCount();

    List<PreSendBean> getPreSendBpoList();

    Integer removePreSendBpoById(Long id);

    void clearReminderUserPreSendList();

    Integer buildReminderUserPreSendList();

    Long getPreSendReminderUserCount();

    List<PreSendBean> getPreSendReminderUserList();

    void removePreSendReminderUserById(Long id);

    void clearFinalRemoveUserPreSendList();

    Integer buildFinalRemoveUserPreSendList();

    Long getPreSendFinalRemoveUserCount();

    List<PreSendBean> getPreSendFinalRemoveUserList();

    void removePreSendFinalRemoveUserById(Long id);

}
