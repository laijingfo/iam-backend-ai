package com.lenovo.service.impl;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.lenovo.bean.*;

import com.lenovo.entity.BatchOperationRequest;
import com.lenovo.entity.UserAccessReview;
import com.lenovo.mapper.BPOMapper;
import com.lenovo.mapper.UarCycleMaintenanceMapper;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.BPOService;
import com.lenovo.service.RegionalUarPolicyService;

import com.lenovo.util.I18nUtil;
import lombok.extern.slf4j.Slf4j;


import org.apache.poi.ooxml.util.SAXHelper;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BPOServiceImpl extends ServiceImpl<BPOMapper, UserAccessReview> implements BPOService {
    private static final String CACHE_PREFIX = "LineManagerReview:query:";
    private static final String LOCK_PREFIX = "Lock:LineManagerReview:";
    private static final String CACHE_PATTERN = "LineManagerReview:query:*";
    private final RedisTemplate<Object, Object> redisTemplate;
    private final RedisConnectionFactory redisConnectionFactory;
    private final UarCycleMaintenanceMapper uarCycleMaintenanceMapper;
    private final RegionalUarPolicyService regionalUarPolicyService;

    @Autowired
    public BPOServiceImpl(RedisTemplate<Object, Object> redisTemplate,
                          RedisConnectionFactory redisConnectionFactory,
                          UarCycleMaintenanceMapper uarCycleMaintenanceMapper,
                          RegionalUarPolicyService regionalUarPolicyService) {

        this.redisTemplate = redisTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
        this.uarCycleMaintenanceMapper = uarCycleMaintenanceMapper;
        this.regionalUarPolicyService = regionalUarPolicyService;
    }

    @Override
    public void exportExcel(HttpServletResponse response, UseAccessReviewBean query, String language) throws IOException {
        regionalUarPolicyService.exportBpoReview(response, query, language);
    }

    @Override
    public RiskManagementStatisticsBean BPOStatistics(Map<String, List<String>> dataRangeMap) {
        return getBaseMapper().BPOStatistics(dataRangeMap);
    }

    @Override
    public Page<UserAccessReview> query(UseAccessReviewBean useAccessReviewBean, Integer page, Integer size) {
        Page p = new Page(page, size);
        return getBaseMapper().query(p, useAccessReviewBean);
    }

    @Override
    @Transactional
    public Map<String, Object> updateBpoAccessReviewStatus(BatchOperationRequest batchOperationRequest) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "操作成功");

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
        int updateReviewStatus = getBaseMapper().updateBpoAccessReviewStatus(batchOperationRequest, reviewItcode, completedCmdbIds);
        System.out.println(updateReviewStatus);
        if (updateReviewStatus < 1) {
            result.put("success", false);
            result.put("message", I18nUtil.get("review.fail"));
            return result;
        } else if (!completedCmdbIds.isEmpty()) {
            result.put("success", true);
            result.put("message", I18nUtil.get("review.success.check_cycle", completedCmdbIds));
        }
        return result;
    }


    /**
     * BPO
     * 导出excel时的列表
     * @param useAccessReviewBean
     * @return
     */
    @Override
    public List<UserAccessReview> getAllLBPOReview(UseAccessReviewBean useAccessReviewBean, String language) {
        return getBaseMapper().getAllBPOReview(useAccessReviewBean, language);
    }
    static {
        IOUtils.setByteArrayMaxOverride(200_000_000); // 200MB
    }
    @Override
    public ImportResult importBPOExcel(MultipartFile file, Map<String, List<String>> dataRangeMap) throws IOException {
        // 创建临时文件
        Path tempFile = Files.createTempFile("excel-import-Bpo" + System.currentTimeMillis(), ".xlsx");
        try {
            // 将上传文件写入临时文件
            file.transferTo(tempFile.toFile());

            // 使用临时文件路径进行流式解析
            return parseBPOExcelStreaming(tempFile, "Bpo_Review", normalizeBpoDataRange(dataRangeMap));
        } finally {
            // 确保删除临时文件
            Files.deleteIfExists(tempFile);
        }
    }
    private ImportResult parseBPOExcelStreaming(
            Path filePath, String operation, Map<String, Set<String>> dataRangeMap
    ) throws IOException {
        List<String> errors = new ArrayList<>();
        List<UserAccessReview> validRecords = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        // 内网使用，文件来源可信，关闭 Zip Bomb 检测以避免误报
        ZipSecureFile.setMinInflateRatio(0);
        try (OPCPackage pkg = OPCPackage.open(filePath.toFile())) {
            XSSFReader reader = new XSSFReader(pkg);
            SharedStrings strings = new ReadOnlySharedStringsTable(pkg);
            StylesTable styles = reader.getStylesTable();

            XMLReader parser = SAXHelper.newXMLReader();
            String reviewItcode = SecurityUtils.getCurrentUserId();

            BPOSheetHandler handler = new BPOSheetHandler(
                    styles, strings, errors, validRecords, successCount, reviewItcode, operation, dataRangeMap
            );
            parser.setContentHandler(new XSSFSheetXMLHandler(styles, strings, handler, false));

            try (InputStream sheetStream = reader.getSheetsData().next()) {
                parser.parse(new InputSource(sheetStream));
            }

            // 处理最后一批记录
            if (!validRecords.isEmpty()) {
                if (Objects.equals(operation, "Bpo_Review")) {
                    getBaseMapper().batchBPOUpdate(validRecords, reviewItcode);
                } else if (Objects.equals(operation, "Update_Bpo_Info")){
                    getBaseMapper().batchUpdateBpoInfo(validRecords, reviewItcode);
                }
                successCount.addAndGet(validRecords.size());
                validRecords.clear();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("Excel解析失败", e);
        }

        deleteRelatedCaches();
        return new ImportResult(successCount.get(), errors.size(), errors);
    }
    private class BPOSheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final StylesTable styles;
        private final SharedStrings strings;
        private final List<String> errors;
        private final List<UserAccessReview> validRecords;
        private final AtomicInteger successCount;
        private final String reviewItcode;
        private final String operation;
        private final Map<String, Set<String>> dataRangeMap;

        private List<String> currentRow = new ArrayList<>();
        private int currentRowIndex = -1;

        // 列索引映射
        private final Map<String, Integer> columnMapping = new HashMap<>();

        // 定义字段与标题的多语言映射
        private final Map<String, List<String>> FIELD_TITLES = Map.of(
                "sequenceNumber", List.of("序列号", "Sequence Number"),
                "appName", List.of("应用名称", "System Name", "App Name"),
                "itCodeOfUser", List.of("用户 ITcode", "User ITcode"),
                "systemRole", List.of("用户角色", "User Role Name"),
                "bpoReviewDecision", List.of("您的审核结果", "Your Review Result"),
                "bpo", List.of("BPO", "bpo"),
                "bpoEmail", List.of("BPO邮箱", "BPO Email")
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

        public BPOSheetHandler(StylesTable styles, SharedStrings strings,
                               List<String> errors, List<UserAccessReview> validRecords,
                               AtomicInteger successCount, String reviewItcode, String operation,
                               Map<String, Set<String>> dataRangeMap) {
            this.styles = styles;
            this.strings = strings;
            this.errors = errors;
            this.validRecords = validRecords;
            this.successCount = successCount;
            this.reviewItcode = reviewItcode;
            this.operation = operation;
            this.dataRangeMap = dataRangeMap;
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
                // 获取列映值
                review.setSequenceNumber(getFieldValue("sequenceNumber"));
                review.setItCodeOfUser(getFieldValue("itCodeOfUser"));
                review.setSystemRole(getFieldValue("systemRole"));

                if (operation.equals("Bpo_Review")){
                    // 获取设置值
                    review.setBpoReviewDecisionParam(getFieldValue("bpoReviewDecision"));

                    // 排除未填写的行
                    if (review.getBpoReviewDecision() == null
                            || review.getBpoReviewDecision().isEmpty()) {
                        return;
                    }
                } else if (operation.equals("Update_Bpo_Info")){
                    // 获取设置值
                    review.setBpo(getFieldValue("bpo").replaceAll("[，,；]", ";") );
                    review.setBpoEmail(getFieldValue("bpoEmail").replaceAll("[，,；]", ";"));
                    // 排除未填写的行
                    if ((review.getBpo() == null || review.getBpo().isEmpty())
                        && (review.getBpoEmail() == null || review.getBpoEmail().isEmpty())
                    ) {
                        return;
                    }
                }

                // 处理当前行
                processBPORow(review, rowIndex + 1, errors, validRecords);

                // 每100行批量处理一次
                if (validRecords.size() >= 100) {
                    if (Objects.equals(operation, "Bpo_Review")) {
                        getBaseMapper().batchBPOUpdate(validRecords, reviewItcode);
                    } else if (Objects.equals(operation, "Update_Bpo_Info")){
                        getBaseMapper().batchUpdateBpoInfo(validRecords, reviewItcode);
                    }
                    successCount.addAndGet(validRecords.size());
                    validRecords.clear();
                }

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

        // ================ BPO行处理逻辑 ================
        private void processBPORow(UserAccessReview row, int rowNum,
                                   List<String> errors, List<UserAccessReview> validRecords) {

            UserAccessReview dbRecord = getBaseMapper().findBySequenceNumber(row.getSequenceNumber());

            if (dbRecord == null) {
                String reason = operation.equals("Bpo_Review") ? "记录不存在或无审核权限" : "记录不存在";
                errors.add(String.format("第%d行: %s (User IT Code: %s, Application ID: %s, System Role: %s)",
                        rowNum, reason, row.getItCodeOfUser(), row.getCmdbId(), row.getSystemRole()));
                return;
            }

            if (operation.equals("Bpo_Review") && !hasBpoReviewPermission(dbRecord, dataRangeMap)) {
                errors.add(String.format(
                        "第%d行: 记录不存在或无审核权限 (User IT Code: %s, Application ID: %s, System Role: %s)",
                        rowNum, row.getItCodeOfUser(), row.getCmdbId(), row.getSystemRole()
                ));
                return;
            }

            UserAccessReview updateRecord = new UserAccessReview();
            updateRecord.setId(dbRecord.getId());

            if (operation.equals("Bpo_Review")){

                if (row.getBpoReviewDecision().equals(dbRecord.getBpoReviewDecision())) {
                    // 数据不变，无需更新
                    return;
                }

                // 获取当前周期任务是否已经完成
                boolean isCompleted = uarCycleMaintenanceMapper.queryUarCompletedCycleByCmdbId(dbRecord.getCmdbId());
                if (isCompleted) {
                    errors.add("第" + rowNum + "行: 周期任务已结束，无法操作");
                    return;
                }
                // 判断是否需要更新 BPO审核结果
                if ( row.getBpoReviewDecision() != null
                        && !row.getBpoReviewDecision().isEmpty()) {
                    // 需要更新的字段
                    updateRecord.setBpoReviewDecision(row.getBpoReviewDecision());

                    validRecords.add(updateRecord);
                }
            } else if (operation.equals("Update_Bpo_Info")){
                // 需要更新的字段, 仅当值有修改时才更新
                if (!row.getBpo().equals(dbRecord.getBpo()) || !row.getBpoEmail().equals(dbRecord.getBpoEmail())) {
                    updateRecord.setBpo(row.getBpo());
                    updateRecord.setBpoEmail(row.getBpoEmail());

                    validRecords.add(updateRecord);
                }
            }

        }
    }

    static Map<String, Set<String>> normalizeBpoDataRange(Map<String, List<String>> dataRangeMap) {
        if (dataRangeMap == null || dataRangeMap.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Set<String>> normalized = new HashMap<>();
        dataRangeMap.forEach((itCode, cmdbIds) -> {
            if (itCode == null || itCode.trim().isEmpty()) {
                return;
            }
            Set<String> normalizedCmdbIds = cmdbIds == null
                    ? Collections.emptySet()
                    : cmdbIds.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .collect(Collectors.toSet());
            normalized.put(itCode.trim().toLowerCase(Locale.ROOT), normalizedCmdbIds);
        });
        return normalized;
    }

    static boolean hasBpoReviewPermission(UserAccessReview record, Map<String, Set<String>> dataRangeMap) {
        if (record == null || dataRangeMap == null || dataRangeMap.isEmpty()) {
            return false;
        }
        Set<String> recordBpos = parseBpoItCodes(record.getBpo());
        String cmdbId = normalizeImportValue(record.getCmdbId());
        for (Map.Entry<String, Set<String>> entry : dataRangeMap.entrySet()) {
            if (!recordBpos.contains(entry.getKey())) {
                continue;
            }
            Set<String> authorizedCmdbIds = entry.getValue();
            if (authorizedCmdbIds.contains("ALL") || authorizedCmdbIds.contains(cmdbId)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> parseBpoItCodes(String bpo) {
        if (bpo == null || bpo.trim().isEmpty()) {
            return Collections.emptySet();
        }
        return Arrays.stream(bpo.split("[;,，；\\s]+"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private static String normalizeImportValue(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public List<AdvancedSearchBean> getDistinctApplication(String bpoReviewStatus,String itCodeOfUser,String bpoReviewDecision) {
        return getBaseMapper().getDistinctApplication(bpoReviewStatus,itCodeOfUser,bpoReviewDecision);
    }

    @Override
    public Boolean updateBpoInfo(BpoInfoBean bpoInfo) {
        // 把数据中的 (，, ；) 替换为(;)
        bpoInfo.setBpo(bpoInfo.getBpo().replaceAll("[，,；]", ";"));
        bpoInfo.setBpoEmail(bpoInfo.getBpoEmail().replaceAll("[，,；]", ";"));
        return getBaseMapper().updateBpoInfo(bpoInfo) > 0;
    }

    @Override
    public ImportResult importBPOInfoExcel(MultipartFile file) throws IOException {
        // 创建临时文件
        Path tempFile = Files.createTempFile("bpo-info-import-" + System.currentTimeMillis(), ".xlsx");
        try {
            // 将上传文件写入临时文件
            file.transferTo(tempFile.toFile());

            // 使用临时文件路径进行流式解析
            return parseBPOExcelStreaming(tempFile, "Update_Bpo_Info", Collections.emptyMap());
        } finally {
            // 确保删除临时文件
            Files.deleteIfExists(tempFile);
        }
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
