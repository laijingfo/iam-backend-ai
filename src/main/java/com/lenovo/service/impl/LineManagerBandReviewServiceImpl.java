package com.lenovo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lenovo.bean.ImportResult;
import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.entity.UserAccessReview;
import com.lenovo.mapper.LineMangerBandReviewMapper;
import com.lenovo.mapper.UarCycleMaintenanceMapper;
import com.lenovo.security.utils.SecurityUtils;
import com.lenovo.service.LineManagerBandReviewService;
import com.lenovo.service.RegionalUarPolicyService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ooxml.util.SAXHelper;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class LineManagerBandReviewServiceImpl extends ServiceImpl<LineMangerBandReviewMapper, UserAccessReview>
        implements LineManagerBandReviewService {

    private final UarCycleMaintenanceMapper uarCycleMaintenanceMapper;
    private final RegionalUarPolicyService regionalUarPolicyService;

    @Override
    public List<UserAccessReview> getAllLineManagerBandReview(UseAccessReviewBean useAccessReviewBean, String language) {
        List<UserAccessReview> reviews = getBaseMapper().getAllLineManagerBandReview(useAccessReviewBean, language);
        reviews.forEach(review -> review.setAccessLabel(
                regionalUarPolicyService.formatAccessLabelForExport(review.getAccessLabel(), language)
        ));
        return reviews;
    }

    public ImportResult importExcel(MultipartFile file) throws IOException {
        // 创建临时文件
        Path tempFile = Files.createTempFile("excel-import-LmBand" + System.currentTimeMillis(), ".xlsx");

        try {
            // 将上传文件写入临时文件
            file.transferTo(tempFile.toFile());

            // 使用临时文件路径进行流式解析
            return parseExcelStreaming(tempFile);
        } finally {
            // 确保删除临时文件
            Files.deleteIfExists(tempFile);
        }
    }
    private ImportResult parseExcelStreaming(Path filePath) throws IOException {
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

            SheetHandler handler = new SheetHandler(styles, strings, errors, validRecords, successCount, reviewItcode);
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

//        deleteRelatedCaches();
        return new ImportResult(successCount.get(), errors.size(), errors);
    }



    private void processSingleRow(UserAccessReview row, int rowNum,
                                  List<String> errors, List<UserAccessReview> validRecords) {
        UserAccessReview dbRecord = getBaseMapper().findBySequenceNumber(
                row.getSequenceNumber()
        );

        if (dbRecord == null) {
            errors.add(String.format("第%d行: 记录不存在 (用户: %s, 应用: %s, 角色: %s)",
                    rowNum + 1, row.getItCodeOfUser(), row.getAppName(), row.getSystemRole()));
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


    private class SheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final StylesTable styles;
        private final SharedStrings strings;
        private final List<String> errors;
        private final List<UserAccessReview> validRecords;
        private final AtomicInteger successCount; // 使用原子整数
        private final String reviewItcode;

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

        // 修正构造函数：添加第五个参数 successCount
        public SheetHandler(StylesTable styles, SharedStrings strings,
                            List<String> errors, List<UserAccessReview> validRecords,
                            AtomicInteger successCount, String reviewItcode) {
            this.styles = styles;
            this.strings = strings;
            this.errors = errors;
            this.validRecords = validRecords;
            this.successCount = successCount;
            this.reviewItcode = reviewItcode;
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
                processSingleRow(review, rowIndex + 1, errors, validRecords);

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

        private String getValue(int index) {
            return (index < currentRow.size()) ? currentRow.get(index) : "";
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



}
