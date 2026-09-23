package com.lenovo.service.impl;

import cn.hutool.core.date.StopWatch;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.bean.apihub.UserProfileData;
import com.lenovo.entity.LenovoUser;
import com.lenovo.mapper.LenovoUserMapper;
import com.lenovo.service.LenovoService;
import com.lenovo.util.ApiHubUtils;
import com.lenovo.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LenovoUserServiceImpl extends ServiceImpl<LenovoUserMapper, LenovoUser>
        implements LenovoService {

    private final ApiHubUtils apiHubUtils;
    private final RedisUtils redisUtils;

    private static final Integer PAGE_INDEX = 1; // 初始页索引
    private static final Integer PAGE_SIZE = 5000; // 每页大小

    /**
     * 同步用户数据, 并且对旧数据备份
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncUserProfileData() {
        StopWatch stopWatch = new StopWatch("SYNC_USER_PROFILE_DATA");

        try {
            UserProfileData firstPage = apiHubUtils.getIUOP_UserProfile(PAGE_INDEX, PAGE_SIZE);
            Integer totalPages = firstPage.getTotalPage();
            stopWatch.start("get data, total:"+firstPage.getTotalCount());

            List<UserProfileData> allData = Collections.synchronizedList(new ArrayList<>());
            allData.add(firstPage);

            ExecutorService executorService = Executors.newFixedThreadPool(2);
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int page = PAGE_INDEX + 1; page <= totalPages; page++) {
                    int finalPage = page;
                    futures.add(executorService.submit(() -> {
                        UserProfileData data = apiHubUtils.getIUOP_UserProfile(finalPage, PAGE_SIZE);
                        allData.add(data);
                    }));
                }
                for (Future<?> future : futures) {
                    future.get();
                }
            } finally {
                // 正常完成时释放线程；出现异常时中断尚未完成的分页任务
                executorService.shutdownNow();
            }
            stopWatch.stop();

            stopWatch.start("init");
            List<LenovoUser> allEntities = new ArrayList<>();
            for (UserProfileData data : allData) {
                allEntities.addAll(new ArrayList<>(data.getList()));
            }

            Integer expectedTotal = firstPage.getTotalCount();
            int actualTotal = allEntities.size();
            if (expectedTotal == null || expectedTotal != actualTotal) {
                throw new IllegalStateException(String.format(
                        "Inconsistent user data quantity, expected quantity:%s, actual quantity:%d",
                        expectedTotal, actualTotal));
            }

            // 先备份，再插入新数据
            baseMapper.backupTable();
            stopWatch.stop();

            stopWatch.start("save");
            this.saveBatch(allEntities);

            // 优化数据
            baseMapper.analyzeData();
            stopWatch.stop();

            log.info("同步用户数据成功: {}", this.count()-1);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("同步用户数据失败", e);
        } finally {
            System.out.println(stopWatch.prettyPrint(TimeUnit.SECONDS));
        }
    }

    @Override
    public LenovoUser getUserInfoByItCode(String itCode) {
        return baseMapper.getUserInfoByItCode(itCode);
    }

}
