package com.lenovo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lenovo.bean.DelegationBean;
import com.lenovo.entity.Delegation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * @Description TODO 授权表服务接口
 * @author wangfenglong
 * @date 2025/11/18 17:15
**/
public interface DelegationService extends IService<Delegation>
{
    Page<Delegation> query(DelegationBean delegationBean, Integer page, Integer size);
    Integer insertDelegation( DelegationBean delegationBean);
    Integer deleteDelegationByUpdateId(Delegation delegation);

    //授权BPO，选择出需要授权的BPO数据
    List<DelegationBean> getCurrentItCodeInUserAccessReviewBpoData(DelegationBean delegationBean);

    //给被授权人发邮件呀
    void sendDelegateeEmail(DelegationBean delegationBean);

    //判断当前登陆账户是否被授权和被授权的cmdbId
    Map<String, String> getDelegationCmdbIdList(String itCode);
}
