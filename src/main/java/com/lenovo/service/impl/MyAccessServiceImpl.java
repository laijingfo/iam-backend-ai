package com.lenovo.service.impl;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.bean.AdvancedSearchBean;
import com.lenovo.bean.ImportResult;
import com.lenovo.bean.RiskManagementStatisticsBean;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.entity.UserAccessReview;
import com.lenovo.mapper.MyAcessMapper;
import com.lenovo.service.MyAccessService;
import com.lenovo.service.RegionalUarPolicyService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class MyAccessServiceImpl extends ServiceImpl<MyAcessMapper, UserAccessReview> implements MyAccessService {
    private static final String CACHE_PREFIX = "LineManagerReview:query:";
    private static final String LOCK_PREFIX = "Lock:LineManagerReview:";
    private static final String CACHE_PATTERN = "LineManagerReview:query:*";
    private final RedisTemplate<Object, Object> redisTemplate;
    private final RedisConnectionFactory redisConnectionFactory;
    private final RegionalUarPolicyService regionalUarPolicyService;

    @Autowired
    public MyAccessServiceImpl(RedisTemplate<Object, Object> redisTemplate,
                               RedisConnectionFactory redisConnectionFactory,
                               RegionalUarPolicyService regionalUarPolicyService) {

        this.redisTemplate = redisTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
        this.regionalUarPolicyService = regionalUarPolicyService;
    }

    @Override
    public RiskManagementStatisticsBean myAccessStatistics(String itCodeOfUser) {
        return getBaseMapper().myAccessStatistics(itCodeOfUser);
    }

    @Override
    public Page<UserAccessReview> query(UseAccessReviewBean useAccessReviewBean, Integer page, Integer size) {
        Page p = new Page(page, size);
        return getBaseMapper().query(p, useAccessReviewBean);
    }

    @Override
    public void updateBPOReview(List<UserAccessReview> userAccessReview) {
        getBaseMapper().updateBPOReview(userAccessReview);
        // 2. 删除相关缓存
        deleteRelatedCaches();
    }

    @Override
    public void batchUpdate1(List<UserAccessReview> userAccessReview) {
        getBaseMapper().batchUpdate1(userAccessReview);
        deleteRelatedCaches();
    }

    /**
     * BPO
     * 导出excel时的列表
     *
     * @param useAccessReviewBean
     * @param language
     * @return
     */
    @Override
    public List<UserAccessReview> getAllLMyAccessReview(UseAccessReviewBean useAccessReviewBean, String language) {
        List<UserAccessReview> reviews = getBaseMapper().getAllLMyAccessReview(useAccessReviewBean, language);
        reviews.forEach(review -> review.setAccessLabel(
                regionalUarPolicyService.formatAccessLabelForExport(review.getAccessLabel(), language)
        ));
        return reviews;
    }

    @Override
    public ImportResult importBPOExcel(MultipartFile file) throws IOException {
        List<UserAccessReview> excelData = parseExcel(file);
        List<String> errors = new ArrayList<>();
        List<UserAccessReview> validRecords = new ArrayList<>();

        for (int i = 0; i < excelData.size(); i++) {
            UserAccessReview excelRow = excelData.get(i);
            int rowNum = i + 2;

            UserAccessReview dbRecord = getBaseMapper().findByUniqueKeys(
                    excelRow.getItCodeOfUser(),
                    excelRow.getAppName(),
                    excelRow.getSystemRole()
            );

            if (dbRecord == null) {
                errors.add("第 " + rowNum + " 行: 记录不存在 (用户: " +
                        excelRow.getItCodeOfUser() + ", 应用: " +
                        excelRow.getAppName() + ", 角色: " +
                        excelRow.getSystemRole() + ")");
                continue;
            }

            if (isUniqueKeyModified(excelRow, dbRecord)) {
                errors.add("第 " + rowNum + " 行: 唯一标识字段不可修改");
                continue;
            }

            excelRow.setSequenceNumber(dbRecord.getSequenceNumber());
            validRecords.add(excelRow);
        }

        if (!validRecords.isEmpty()) {
            getBaseMapper().batchBPOUpdate(validRecords);
            deleteRelatedCaches();
        }

        return new ImportResult(
                validRecords.size(),
                excelData.size() - validRecords.size(),
                errors
        );
    }

    @Override
    public List<AdvancedSearchBean> getDistinctApplication(String finalReviewStatus,String itCodeOfUser,String finalReviewDecision) {
        return getBaseMapper().getDistinctApplication(finalReviewStatus,itCodeOfUser,finalReviewDecision);
    }

    private List<UserAccessReview> parseExcel(MultipartFile file) throws IOException {
        List<UserAccessReview> reviews = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                UserAccessReview review = new UserAccessReview();
                review.setAppName(getCellStringValue(row, 0));
                review.setItCodeOfUser(getCellStringValue(row, 1));
                review.setUserName(getCellStringValue(row, 2));
                review.setUserId(getCellStringValue(row, 3));
                review.setCountry(getCellStringValue(row, 4));
                review.setDepartment(getCellStringValue(row, 5));
                review.setSystemRole(getCellStringValue(row, 6));
                review.setRoleDescription(getCellStringValue(row, 7));
                review.setAccessLabel(getCellStringValue(row, 8));
                review.setLineManager(getCellStringValue(row, 9));
                review.setSecondLineManager(getCellStringValue(row, 10));
                review.setLineManagerReviewStatus(getCellStringValue(row, 11));
                review.setLineManagersReviewDecision(getCellStringValue(row, 12));
                review.setBpoReviewStatus(getCellStringValue(row, 13));
                review.setBpoReviewDecision(getCellStringValue(row, 14));


                reviews.add(review);
            }
        }
        return reviews;
    }
    private String getCellStringValue(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) return "";

        // 使用 DataFormatter 获取格式化后的字符串值
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell);
    }

    private boolean isUniqueKeyModified(UserAccessReview excel, UserAccessReview db) {
        return !excel.getItCodeOfUser().equals(db.getItCodeOfUser()) ||
                !excel.getAppName().equals(db.getAppName()) ||
                !excel.getSystemRole().equals(db.getSystemRole());
    }

    @Async
    public void deleteRelatedCaches() {
        try {
            Set<Object> keys = new HashSet<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(CACHE_PATTERN)
                    .count(100)
                    .build();

            // 使用正确的 SCAN 实现
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                Cursor<byte[]> cursor = connection.scan(options);
                while (cursor.hasNext()) {
                    byte[] keyBytes = cursor.next();
                    Object key = redisTemplate.getKeySerializer().deserialize(keyBytes);
                    if (key != null) {
                        keys.add(key);
                    }
                }
                return null;
            });

            if (!keys.isEmpty()) {
                deleteKeysInBatches(keys, 100);
                log.info("Deleted {} cache keys", keys.size());
            }
        } catch (Exception e) {
            log.error("Failed to delete cache keys", e);
        }
    }

    private void deleteKeysInBatches(Set<Object> keys, int batchSize) {
        List<Object> keyList = new ArrayList<>(keys);
        int total = keyList.size();
        int batches = (int) Math.ceil((double) total / batchSize);

        for (int i = 0; i < batches; i++) {
            int fromIndex = i * batchSize;
            int toIndex = Math.min(fromIndex + batchSize, total);
            List<Object> batch = keyList.subList(fromIndex, toIndex);

            if (!batch.isEmpty()) {
                redisTemplate.delete(batch);
            }
        }
    }

}
