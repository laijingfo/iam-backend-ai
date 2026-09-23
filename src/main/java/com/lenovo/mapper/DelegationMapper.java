package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lenovo.bean.DelegationBean;
import com.lenovo.entity.Delegation;
import com.lenovo.entity.UserAccessReview;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
* @author mercury
* @description 针对表【risk_management】的数据库操作Mapper
* @createDate 2024-10-28 11:38:48
* @Entity com.lenovo.entity.RiskManagement
*/
public interface DelegationMapper extends BaseMapper<Delegation>
{
    Page<Delegation> query(Page p, @Param("DelegationBean") DelegationBean delegationBean);

    Integer insertDelegation(@Param("DelegationBean") DelegationBean delegationBean);

    //根据被授权人查询授权数据
    List<Delegation> getDelegationByDelegatee(@Param("DelegationBean") DelegationBean delegationBean);

    //用户登陆获取权限
    List<Delegation> getDelegationByDelegateeAndDelegationDate(@Param("DelegationBean") DelegationBean delegationBean);

    List<Delegation> getDelegationByReapeat(@Param("DelegationBean") DelegationBean delegationBean);

    //发送通知页面：发送邮件：给LineManager或者BPO发邮件时候，查询是否授权给别人，授权给别人则也要发邮件
    List<Delegation> getDelegateeBySendEmail(@Param("DelegationBean") DelegationBean delegationBean);

    //授权BPO，选择出需要授权的BPO数据
    List<DelegationBean> getBpsByUserAccessReview(@Param("DelegationBean") DelegationBean delegationBean);

    //查询被授权人的cmdbID集合
    List<String> getCmdbIdByBps(@Param("DelegationBean") DelegationBean delegationBean);

    //查询当前登陆账户在当前时间内是否被授权
    List<Delegation> getDelegationByDataAndBps(@Param("DelegationBean") DelegationBean delegationBean);

    //根据bpo查询出被授权人集合
    @MapKey("bpo")
    Map<String, Map<String,String>> getDelegateeByBpo(@Param("allowSendEmail")List<UserAccessReview> allowSendEmail);
}




