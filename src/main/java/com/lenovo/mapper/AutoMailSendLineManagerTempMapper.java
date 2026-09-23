package com.lenovo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lenovo.entity.AutoMailSendBpoTemp;
import com.lenovo.entity.AutoMailSendLineManagerTemp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * @Description TODO 自动定时任务发邮件的mapper(和发邮件有关的应该都写到这里)
 * @author wangfenglong
 * @date 2025/12/30 10:52
**/
@Mapper
public interface AutoMailSendLineManagerTempMapper extends BaseMapper<AutoMailSendLineManagerTemp>
{
    /**
     * @Description TODO 获取要发送邮件的lineManager的数据
     * @author wangfenglong
     * @date 2025/12/30 10:52
    **/
    List<AutoMailSendLineManagerTemp> getLinaManagerSendData();

    /**
     * @Description TODO 获取要发送邮件的Bpo的数据
     * @author wangfenglong
     * @date 2025/12/30 15:20
    **/
    List<AutoMailSendBpoTemp> getBpoSendData();

    /**
     * 批量新增邮件发送记录
     * @param list 邮件发送记录列表
     * @return 影响行数
     * @author wangfenglong
     */
    int batchInsert(List<AutoMailSendLineManagerTemp> list);
    int batchInsertBpo(List<AutoMailSendBpoTemp> list);

    /**
     * 根据状态查询邮件发送记录列表
     * @param status 状态：0-待发送 1-发送中 2-发送成功 3-发送失败
     * @return 邮件发送记录列表
     * @author wangfenglong
     */
    List<AutoMailSendLineManagerTemp> selectListByStatus(Integer status);

    /**
     * 批量更新邮件状态
     * @param status 目标状态
     * @param idList 待更新的记录ID列表
     * @return 影响行数
     * @author wangfenglong
     */
    int batchUpdateStatus(@Param("status") Integer status, @Param("idList") List<Long> idList);

    /**
     * 批量删除邮件发送记录（根据ID列表）
     * @param idList 待删除的记录ID列表
     * @return 影响行数
     * @author wangfenglong
     */
    int batchDeleteByIds(List<Long> idList);

    @Update("TRUNCATE TABLE auto_mail_send_lineManager_temp,auto_mail_send_bpo_temp")
    void truncateTempTable();

    /**
     * @Description TODO 查询ad_tb_upp_nature_2用户表获取高管(不能给高管发底层牛马人的邮件)
     * @author wangfenglong
     * @date 2025/12/30 10:53
    **/
    @Select("SELECT user_name FROM ad_tb_upp_nature_2 where 1=1 and band_flag = #{flag}")
    List<String> getUserBySVPBandFlag(@Param("flag")String flag);

    @Select("SELECT email FROM ad_tb_upp_nature_2 where 1=1 and band_flag = #{flag}")
    List<String> getSVPEmailBySVPBandFlag(@Param("flag")String flag);

    @Select("SELECT * FROM auto_mail_send_bpo_temp")
    List<AutoMailSendBpoTemp> getBpoFromTable();

    @Select("SELECT * FROM auto_mail_send_linemanager_temp")
    List<AutoMailSendLineManagerTemp> getLMFromTable();

    /**
     * 原子抢占待发送邮件（PostgreSQL核心语法）
     * 锁定符合条件的行，跳过已被其他节点锁定的行，保证原子性
     */
    @Select(" SELECT * FROM auto_mail_send_lineManager_temp WHERE status = 0 LIMIT #{batchSize} FOR UPDATE SKIP LOCKED")
    List<AutoMailSendLineManagerTemp> preemptMails(@Param("batchSize") int batchSize);

    /**
     * 更新邮件状态（抢占后标记为“发送中”）
     */
    @Update(" UPDATE auto_mail_send_lineManager_temp SET status = 1, process_node = #{nodeId}, process_time = NOW() WHERE id = #{mailId}")
    int markSending(@Param("mailId") Long mailId, @Param("nodeId") String nodeId);

    /**
     * 更新邮件最终发送状态（成功/失败）
     */
    @Update(" UPDATE auto_mail_send_lineManager_temp SET status = #{status}, retry_count = #{retryCount} WHERE id = #{mailId}")
    int updateMailStatus(@Param("mailId") Long mailId, @Param("status") Integer status, @Param("retryCount") Integer retryCount);

    /**
     * 兜底：重置超时的“发送中”邮件为待发送（节点挂掉时恢复）
     * PostgreSQL用AGE函数计算时间差：AGE(NOW(), process_time) > INTERVAL '${timeout} seconds'
     */
    //@Update(" UPDATE auto_mail_send_lineManager_temp SET status = 0, process_node = NULL, process_time = NULL WHERE status = 1 AND AGE(NOW(), process_time) > INTERVAL '${timeout} seconds'")
    @Update(" UPDATE auto_mail_send_lineManager_temp SET status = 0, process_node = NULL, process_time = NULL WHERE status = 1 AND AGE(NOW(), process_time) > (INTERVAL '1 second' * #{timeout})")
    int resetTimeoutMails(@Param("timeout") Integer timeout);

    //*******************************************************************************************************************************//
    @Select(" SELECT * FROM auto_mail_send_bpo_temp WHERE status = 0 LIMIT #{batchSize} FOR UPDATE SKIP LOCKED")
    List<AutoMailSendBpoTemp> preemptMailsToBpo(@Param("batchSize") int batchSize);

    @Update(" UPDATE auto_mail_send_bpo_temp SET status = 1, process_node = #{nodeId}, process_time = NOW() WHERE id = #{mailId}")
    int markSendingToBpo(@Param("mailId") Long mailId, @Param("nodeId") String nodeId);

    @Update(" UPDATE auto_mail_send_bpo_temp SET status = #{status}, retry_count = #{retryCount} WHERE id = #{mailId}")
    int updateMailStatusToBpo(@Param("mailId") Long mailId, @Param("status") Integer status, @Param("retryCount") Integer retryCount);

    @Update(" UPDATE auto_mail_send_bpo_temp SET status = 0, process_node = NULL, process_time = NULL WHERE status = 1 AND AGE(NOW(), process_time) > (INTERVAL '1 second' * #{timeout})")
    int resetTimeoutMailsToBpo(@Param("timeout") Integer timeout);
    //*******************************************************************************************************************************//

}