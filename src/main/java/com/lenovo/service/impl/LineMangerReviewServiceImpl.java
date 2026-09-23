package com.lenovo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.bean.AdvancedSearchBean;
import com.lenovo.bean.ImportResult;
import com.lenovo.bean.RiskManagementStatisticsBean;
import com.lenovo.bean.UseAccessReviewBean;

import com.lenovo.entity.BatchOperationRequest;
import com.lenovo.entity.UserAccessReview;
import com.lenovo.mapper.LineMangerReviewMapper;
import com.lenovo.mapper.UarCycleMaintenanceMapper;
import com.lenovo.security.utils.RoleUtils;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.LineMangerReviewService;
import com.lenovo.service.RegionalUarPolicyService;
import com.lenovo.util.I18nUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ooxml.util.SAXHelper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

@Slf4j
@Service
@RequiredArgsConstructor
public class LineMangerReviewServiceImpl extends ServiceImpl<LineMangerReviewMapper, UserAccessReview>
        implements LineMangerReviewService {


    private static final String CACHE_PREFIX = "LineManagerReview:query:";
    private static final String LOCK_PREFIX = "Lock:LineManagerReview:";
    private static final String CACHE_PATTERN = "LineManagerReview:query:*";
    private final UarCycleMaintenanceMapper uarCycleMaintenanceMapper;
    private final RedisTemplate<Object, Object> redisTemplate;
    private final RegionalUarPolicyService regionalUarPolicyService;


    @Override
    public void exportExcel(HttpServletResponse response, UseAccessReviewBean query, String language) throws IOException {
        regionalUarPolicyService.exportLineManagerReview(response, query, language);
    }

    /**
     * 使用redis做缓存查询
     * @param useAccessReviewBean
     * @param page
     * @param size
     * @return
     */
    @Override
    public Page<UserAccessReview> query(UseAccessReviewBean useAccessReviewBean, Integer page, Integer size) {
        // 1. 查询数据库
        return queryFromDB(useAccessReviewBean, page, size);

    }


    private Page<UserAccessReview> getCachedPage(String cacheKey) {
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof Page) {
                return (Page<UserAccessReview>) cached;
            }
        } catch (Exception e) {
            log.error("获取缓存失败: {}");
        }
        return null;
    }

    private void cacheResult(String cacheKey, Page<UserAccessReview> resultPage) {
        try {
            redisTemplate.opsForValue().set(cacheKey, resultPage, 300, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("缓存写入失败: {}");
        }
    }

    private Page<UserAccessReview> queryFromDB(UseAccessReviewBean bean, Integer page, Integer size) {
        Page<UserAccessReview> p = new Page<>(page, size);
        return getBaseMapper().query(p, bean);
    }

    private String buildCacheKey(UseAccessReviewBean bean, Integer page, Integer size) {
        return CACHE_PREFIX +
                Objects.toString(bean.getItCodeOfUser(), "null") + ":" +
                Objects.toString(bean.getAppName(), "null") + ":" +
                Objects.toString(bean.getCmdbId(), "null") + ":" +
                Objects.toString(bean.getLineManagerReviewStatus(), "null") + ":" +
                Objects.toString(bean.getLineManagersReviewDecision(), "null") + ":" +
                Objects.toString(bean.getBpoReviewDecision(), "null") + ":" +
                Objects.toString(bean.getBpoReviewStatus(), "null") + ":" +
                Objects.toString(bean.getDataRange(), "null") + ":" +
                page + ":" + size;
    }

    private boolean acquireLock(String lockKey, long waitSeconds) {
        long endTime = System.currentTimeMillis() + waitSeconds * 1000;
        while (System.currentTimeMillis() < endTime) {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    lockKey, "locked", 10, TimeUnit.SECONDS
            );
            if (Boolean.TRUE.equals(acquired)) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return false;
    }

    private void releaseLock(String lockKey) {
        redisTemplate.delete(lockKey);
    }










    /**
     * 导出excel时的列表
     * @param useAccessReviewBean
     * @return
     */
    @Override
    public List<UserAccessReview> getAllLineManagerReview(UseAccessReviewBean useAccessReviewBean, String language) {
        return getBaseMapper().getAllLineManagerReview(useAccessReviewBean, language);
    }




    @Override
    @Transactional
    public Map<String, Object> updateLmAccessReviewStatus(BatchOperationRequest batchOperationRequest) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", I18nUtil.get("common.success"));

        // 1. 获取当前登录用户
        String reviewItcode = SecurityUtils.getCurrentUserId();

        // 2. 检测是否有Review能力
        int reviewAbility = baseMapper.checkReviewAbility(batchOperationRequest);
        if (reviewAbility < 1) {
            result.put("success", false);
            result.put("message", I18nUtil.get("review.fail.no_ability"));
            return result;
        }

        // 3. 检查应用在当前周期是否已经完成终审
        List<String> completedCmdbIds = baseMapper.getCompletedCmdbIdByConditions(batchOperationRequest);
        if (!completedCmdbIds.isEmpty()) {
            result.put("success", false);
            result.put("message", I18nUtil.get("review.fail.check_cycle", completedCmdbIds));
            return result;
        }

        // 4. 更新审核结果
        int updateReviewStatus = getBaseMapper().updateLmAccessReviewStatus(batchOperationRequest, reviewItcode, completedCmdbIds);
        if (updateReviewStatus < 1) {
            result.put("success", false);
            result.put("message", I18nUtil.get("review.fail"));
            return result;
        } else if (!completedCmdbIds.isEmpty()) {
            result.put("success", true);
            result.put("message", I18nUtil.get("review.success.check_cycle", completedCmdbIds));
        }
        // 4. 删除相关缓存
        deleteRelatedCaches();

        return result;
    }

    @Override
    public RiskManagementStatisticsBean UseAccessReviewStatistics(List<String> dataRange) {
        return getBaseMapper().UseAccessReviewStatistics(dataRange);
    }
    static {
        IOUtils.setByteArrayMaxOverride(200_000_000); // 200MB
    }

    @Override

    public ImportResult importExcel(MultipartFile file, List<String> dataRange) throws IOException {
        // 创建临时文件
        Path tempFile = Files.createTempFile("excel-import-Lm" + System.currentTimeMillis(), ".xlsx");
        try {
            // 将上传文件写入临时文件
            file.transferTo(tempFile.toFile());

            // 使用临时文件路径进行流式解析
            return parseExcelStreaming(tempFile, normalizeLineManagerDataRange(dataRange));
        } finally {
            // 确保删除临时文件
            Files.deleteIfExists(tempFile);
        }
    }
    private ImportResult parseExcelStreaming(Path filePath, Set<String> dataRange) throws IOException {
        List<String> errors = new ArrayList<>();
        List<UserAccessReview> validRecords = new ArrayList<>();
        // 使用 AtomicInteger 替代基本类型 int
        AtomicInteger successCount = new AtomicInteger(0);

        // 内网使用，文件来源可信，关闭 Zip Bomb 检测以避免误报
        ZipSecureFile.setMinInflateRatio(0);
        try (OPCPackage pkg = OPCPackage.open(filePath.toFile())) {
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings strings = new ReadOnlySharedStringsTable(pkg);
            StylesTable styles = reader.getStylesTable();

            XMLReader parser = SAXHelper.newXMLReader();
            // 修正参数：添加 successCount 作为第五个参数
            String reviewItcode = SecurityUtils.getCurrentUserId();

            SheetHandler handler = new SheetHandler(
                    styles, strings, errors, validRecords, successCount, reviewItcode, dataRange
            );
            parser.setContentHandler(new XSSFSheetXMLHandler(styles, strings, handler, false));

            try (InputStream sheetStream = reader.getSheetsData().next()) {
                parser.parse(new InputSource(sheetStream));
            }

            // 处理最后一批记录
            if (!validRecords.isEmpty()) {
                getBaseMapper().batchUpdate(validRecords, reviewItcode);
                successCount.addAndGet(validRecords.size());
                validRecords.clear();
            }
        } catch (Exception e) {
            throw new IOException("Excel解析失败", e);
        }

        deleteRelatedCaches();
        return new ImportResult(successCount.get(), errors.size(), errors);
    }



    private void processSingleRow(UserAccessReview row, int rowNum,
                                  List<String> errors, List<UserAccessReview> validRecords,
                                  Set<String> dataRange) {
        UserAccessReview dbRecord = getBaseMapper().findBySequenceNumber(row.getSequenceNumber());

        if (dbRecord == null || !hasLineManagerReviewPermission(dbRecord, dataRange)) {
            errors.add(String.format("第%d行: 记录不存在或无审核权限 (用户: %s, 应用: %s, 角色: %s)",
                    rowNum, row.getItCodeOfUser(), row.getAppName(), row.getSystemRole()));
            return;
        }

        if (row.getLineManagersReviewDecision().equals(dbRecord.getLineManagersReviewDecision())) {
            // 数据不变，无需更新
            return;
        }

        // 获取当前周期任务是否已经完成
        boolean isCompleted = uarCycleMaintenanceMapper.queryUarCompletedCycleByCmdbId(dbRecord.getCmdbId());
        if (isCompleted) {
            errors.add("第" + rowNum + "行: 周期任务已结束，无法操作");
            return;
        }

        if ( row.getLineManagersReviewDecision() != null
            && (!"".equals(row.getLineManagersReviewDecision()))
        ) {
            // 创建更新对象，只包含需要更新的字段
            UserAccessReview updateRecord = new UserAccessReview();
            updateRecord.setId(dbRecord.getId());
            updateRecord.setLineManagersReviewDecision(row.getLineManagersReviewDecision());

            validRecords.add(updateRecord);
        }

    }

    static Set<String> normalizeLineManagerDataRange(List<String> dataRange) {
        if (dataRange == null || dataRange.isEmpty()) {
            return Collections.emptySet();
        }
        return dataRange.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
    }

    static boolean hasLineManagerReviewPermission(UserAccessReview record, Set<String> dataRange) {
        if (record == null || dataRange == null || dataRange.isEmpty()) {
            return false;
        }
        return dataRange.contains(normalizeImportValue(record.getLineManager()));
    }

    private static String normalizeImportValue(String value) {
        return value == null ? "" : value.trim();
    }



    private class SheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final StylesTable styles;
        private final SharedStrings strings;
        private final List<String> errors;
        private final List<UserAccessReview> validRecords;
        private final AtomicInteger successCount; // 使用原子整数
        private final String reviewItcode;
        private final Set<String> dataRange;

        private List<String> currentRow = new ArrayList<>();
        private int currentRowIndex = -1;

        // 列索引映射
        private final Map<String, Integer> columnMapping = new HashMap<>();

        // 定义字段与标题的多语言映射
        private final Map<String, List<String>> FIELD_TITLES = Map.of(
                "sequenceNumber", List.of("序列号", "Sequence Number"),
                "appName", List.of("应用名称", "System Name"),
                "itCodeOfUser", List.of("用户 ITcode", "User ITcode"),
                "systemRole", List.of("用户角色", "User Role Name"),
                "lineManagerReviewDecision", List.of("您的审核结果", "Your Review Result")
        );

        private boolean isHeaderRow(List<String> row) {
            // 遍历字段标题映射，检查是否有匹配的列标题
            for (List<String> titles : FIELD_TITLES.values()) {
                for (String title : titles) {
                    if (row.contains(title)) {
                        return true; // 找到匹配的标题，说明是表头行
                    }
                }
            }
            return false; // 没有匹配的标题，说明不是表头行
        }

        public SheetHandler(StylesTable styles, SharedStrings strings,
                            List<String> errors, List<UserAccessReview> validRecords,
                            AtomicInteger successCount, String reviewItcode, Set<String> dataRange) {
            this.styles = styles;
            this.strings = strings;
            this.errors = errors;
            this.validRecords = validRecords;
            this.successCount = successCount;
            this.reviewItcode = reviewItcode;
            this.dataRange = dataRange;
        }



        @Override
        public void startRow(int rowIndex) {
            currentRowIndex = rowIndex;
            currentRow.clear();
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int columnIndex = CellReference.convertColStringToIndex(
                    cellReference.replaceAll("\\d", ""));

            // 填充缺失的列
            while (currentRow.size() <= columnIndex) {
                currentRow.add("");
            }
            currentRow.set(columnIndex, formattedValue);
        }

        @Override
        public void endRow(int rowIndex) {
            try {
                // 动态判断表头行
                if (columnMapping.isEmpty()) {
                    if (isHeaderRow(currentRow)) {
                        mapHeadersToFields(currentRow); // 建立字段到列索引的映射
                    }
                    return; // 无论是否是表头行，跳过当前行
                }
                UserAccessReview review = new UserAccessReview();
                review.setSequenceNumber(getFieldValue("sequenceNumber"));
                review.setAppName(getFieldValue("appName"));
                review.setItCodeOfUser(getFieldValue("itCodeOfUser"));
                review.setSystemRole(getFieldValue("systemRole"));
                if (getFieldValue("lineManagerReviewDecision") != null && !getFieldValue("lineManagerReviewDecision").isEmpty()) {
                    review.setLineManagersReviewDecisionParam(getFieldValue("lineManagerReviewDecision"));
                }

                // 排除未填写的行
                if (review.getLineManagersReviewDecision() == null
                        || "".equals(review.getLineManagersReviewDecision())) {
                    return;
                }

                // 处理当前行
                processSingleRow(review, rowIndex + 1, errors, validRecords, dataRange);

                // 每100行批量处理一次
                if (validRecords.size() >= 100) {
                    getBaseMapper().batchUpdate(validRecords, reviewItcode);
                    // 使用原子操作更新计数
                    successCount.addAndGet(validRecords.size());
                    validRecords.clear();
                }

            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e.getMessage());
            } finally {
                currentRow.clear();
            }
        }

        private void mapHeadersToFields(List<String> headerRow) {
            for (int i = 0; i < headerRow.size(); i++) {
                String header = headerRow.get(i);
                int finalI = i;
                FIELD_TITLES.forEach((field, titles) -> {
                    if (titles.contains(header)) {
                        columnMapping.put(field, finalI);
                    }
                });
            }
        }

        private String getFieldValue(String field) {
            Integer columnIndex = columnMapping.get(field);
            return (columnIndex != null && columnIndex < currentRow.size()) ? currentRow.get(columnIndex) : "";
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
            // 不需要实现
        }
    }


    @Override
    public List<AdvancedSearchBean> getDistinctApplication(String lineManagerReviewStatus,String itCodeOfUser,String lineManagersReviewDecision) {
        return getBaseMapper().getDistinctApplication(lineManagerReviewStatus,itCodeOfUser,lineManagersReviewDecision);
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

    /**
     * 分批删除键
     */
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
