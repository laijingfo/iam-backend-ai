    package com.lenovo.service;

    import com.lenovo.entity.AllowSendEmailForBpo;
    import com.lenovo.entity.UarMailTemplate;

    import java.util.Map;
    import java.util.Set;
    import java.util.concurrent.ConcurrentHashMap;

    public interface UarMailService
    {
        void sendTemplatedEmail(Long templateId, Set<String> recipients, Set<String> ccs, Map<String, String> variables, String batchNo);

        //只能用来给UAR管理——待发送页面，发送失败页面，UAR周期设置这三个地方发邮件统一入口，别的应用还是用上面的发送方法(没时间统一改造)
        void sendTemplatedEmailOnlyForLmBpoAndUARSetting(UarMailTemplate templatete, Set<String> recipients, Set<String> ccs, Map<String, String> variables, String batchNo,String sendFlag);


        //只能用来给【最终审核结果是移除的用户】发送邮件，因为需要传送excel附件
        void sendTemplatedEmailOnlyFinalRemoveUserByExcel(byte[] excelBytes, String userItCode, UarMailTemplate template, Set<String> recipients, Set<String> ccs, Map<String, String> variables, String batchNo,String sendFlag);
    }