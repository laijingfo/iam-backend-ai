package com.lenovo.util;

import com.lenovo.bean.ExcelFileBean;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import java.net.URLEncoder;
@Slf4j
public class ExcelUtil {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * 导出审核结果(专用与 LM BPO USER LMSVP)
     * @param response      响应对象
     * @param fileName      文件名
     * @param sheetName     sheet名称
     * @param dataList      数据
     * @param headers       标题
     * @param fields        字段
     * @param headerTips    顶部提示
     * @param language      语言
     */
    public static <T> void exportReviewToExcel(
            HttpServletResponse response,
            String fileName,
            String sheetName,
            List<T> dataList,
            String[] headers,
            String[] fields,
            String headerTips,
            String  language
    ) throws IOException {
        int rowAccessWindowSize = 500;
        SXSSFWorkbook workbook = new SXSSFWorkbook(rowAccessWindowSize);
        workbook.setCompressTempFiles(true);
        Sheet sheet = workbook.createSheet(sheetName);

        // 标题行样式
        CellStyle headerStyle = createHeaderStyle(workbook);

        // 可编辑样式（解锁）
        CellStyle unlockedCellStyle = workbook.createCellStyle();
        unlockedCellStyle.setLocked(false);


        // 不可编辑样式（锁定）
        CellStyle lockedCellStyle = workbook.createCellStyle();
        lockedCellStyle.setLocked(true);

        // 提前创建好所有组合样式，避免在循环里重复创建
        // 1. 可编辑 + 换行样式
        CellStyle unlockedWrapStyle = workbook.createCellStyle();
        unlockedWrapStyle.cloneStyleFrom(unlockedCellStyle);  // 复制基础样式
        unlockedWrapStyle.setWrapText(true);
        unlockedWrapStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        unlockedWrapStyle.setAlignment(HorizontalAlignment.LEFT);

        // 2. 可编辑 + 日期样式
        CellStyle unlockedDateStyle = workbook.createCellStyle();
        unlockedDateStyle.cloneStyleFrom(unlockedCellStyle);
        unlockedDateStyle.setDataFormat(workbook.getCreationHelper()
                .createDataFormat().getFormat("yyyy-MM-dd HH:mm:ss"));
        unlockedDateStyle.setAlignment(HorizontalAlignment.LEFT);

        // 3. 锁定 + 换行样式
        CellStyle lockedWrapStyle = workbook.createCellStyle();
        lockedWrapStyle.cloneStyleFrom(lockedCellStyle);
        lockedWrapStyle.setWrapText(true);
        lockedWrapStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        lockedWrapStyle.setAlignment(HorizontalAlignment.LEFT);

        // 4. 锁定 + 日期样式
        CellStyle lockedDateStyle = workbook.createCellStyle();
        lockedDateStyle.cloneStyleFrom(lockedCellStyle);
        lockedDateStyle.setDataFormat(workbook.getCreationHelper()
                .createDataFormat().getFormat("yyyy-MM-dd HH:mm:ss"));
        lockedDateStyle.setAlignment(HorizontalAlignment.LEFT);

        // 5. 纯锁定样式（用于数字、布尔等）
        CellStyle lockedDefaultStyle = lockedCellStyle;  // 直接用

        // 6. 纯解锁样式
        CellStyle unlockedDefaultStyle = unlockedCellStyle;  // 直接用

        // 标题行索引
        int headerIndex = 0;

        // 创建顶部提示
        if (headerTips != null) {
            int headerTipsIndex = 0;
            headerIndex = 1;
            Row headerTipsRow = sheet.createRow(headerTipsIndex);
            Cell headerTipsRowCell = headerTipsRow.createCell(0);
            headerTipsRowCell.setCellValue(headerTips);
            headerTipsRow.setHeightInPoints(22);
        }
        sheet.createFreezePane(0, headerIndex + 1);

        // 创建标题行
        Row headerRow = sheet.createRow(headerIndex);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 25 * 256);

            headerRow.setHeightInPoints(25);
        }

        // 找到 "Your Review Decision" 列索引
        int editableColIndex = -1;
        int systemRoleColIndex = -1;
        for (int i = 0; i < headers.length; i++) {
            if ("Your Review Result".equalsIgnoreCase(headers[i]) ||
                    "您的审核结果".equals(headers[i])) {
                editableColIndex = i;
                break;
            }
        }
        // 找到 "系统角色" 列索引
        for (int i = 0; i < headers.length; i++) {
            if ("角色描述".equals(headers[i]) || "Role Description".equalsIgnoreCase(headers[i])) {
                systemRoleColIndex = i;
                break;
            }
        }

        int totalRows = dataList.size();
        int rowNum = headerIndex + 1;

        // 摘出Cell Style，按需调用，防止异常：The maximum number of Cell Styles was exceeded. You can define up to 64000 style in a .xlsx Workbook
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(workbook.getCreationHelper()
                .createDataFormat().getFormat("yyyy-MM-dd HH:mm:ss"));
        dateStyle.setAlignment(HorizontalAlignment.LEFT);

        // 先写入所有数据
        for (T item : dataList) {
            Row row = sheet.createRow(rowNum++);
            row.setHeightInPoints(35);

            for (int col = 0; col < fields.length; col++) {
                Object value = getFieldValue(item, fields[col]);
                Cell cell = row.createCell(col);
                boolean isEditable = (col == editableColIndex);

                if (value == null) {
                    cell.setCellValue("");
                    cell.setCellStyle(isEditable ? unlockedDefaultStyle : lockedDefaultStyle);
                } else if (value instanceof Number) {
                    cell.setCellValue(((Number) value).doubleValue());
                    cell.setCellStyle(isEditable ? unlockedDefaultStyle : lockedDefaultStyle);
                } else if (value instanceof Date || value instanceof LocalDateTime) {
                    if (value instanceof Date) {
                        cell.setCellValue((Date) value);
                    } else {
                        cell.setCellValue((LocalDateTime) value);
                    }
                    cell.setCellStyle(isEditable ? unlockedDateStyle : lockedDateStyle);
                } else if (value instanceof Boolean) {
                    cell.setCellValue((Boolean) value);
                    cell.setCellStyle(isEditable ? unlockedDefaultStyle : lockedDefaultStyle);
                } else {
                    // 字符串类型：用换行样式
                    cell.setCellValue(value.toString());
                    cell.setCellStyle(isEditable ? unlockedWrapStyle : lockedWrapStyle);
                }
            }
        }
        // 刷新所有行
        ((SXSSFSheet) sheet).flushRows(totalRows);

        // 设置自动筛选
        sheet.setAutoFilter(new CellRangeAddress(headerIndex, headerIndex + totalRows, 0, headers.length - 1));

        // 给 "Your Review Decision" 列添加下拉框 keep/remove
        if (editableColIndex != -1 && totalRows > 0) {
            DataValidationHelper dvHelper = sheet.getDataValidationHelper();
            String[] options =null;;
            if (language.equals("EN")) {
                options = new String[]{"keep", "remove"};
            }
            else {
                options = new String[]{"保留", "移除"};
            }

            DataValidationConstraint dvConstraint = dvHelper.createExplicitListConstraint(options);

            // 设置数据验证范围
            CellRangeAddressList addressList = new CellRangeAddressList(headerIndex + 1, headerIndex + totalRows, editableColIndex, editableColIndex);
            DataValidation validation = dvHelper.createValidation(dvConstraint, addressList);

            // 针对Office兼容性的特殊处理
            if (validation instanceof XSSFDataValidation) {
                validation.setSuppressDropDownArrow(true);
                validation.setShowErrorBox(true);
            } else {
                validation.setSuppressDropDownArrow(false);
            }

            // 重要：设置允许多选为false，提高Office兼容性
            validation.setEmptyCellAllowed(true);
            validation.setSuppressDropDownArrow(true);
            validation.setShowErrorBox(true);

            // 添加验证到工作表
            sheet.addValidationData(validation);
        }

        // 保护工作表（允许筛选）
        sheet.protectSheet("lock123"); // 密码可修改
        ((SXSSFSheet) sheet).lockAutoFilter(false);   // 保留筛选功能
        ((SXSSFSheet) sheet).lockFormatCells( false);   // 允许设置单元格格式
        ((SXSSFSheet) sheet).lockFormatColumns( false); // 允许调整列宽
        ((SXSSFSheet) sheet).lockFormatRows( false);    // 允许调整行高

        ((SXSSFSheet) sheet).enableLocking();

        // 响应头
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String encodedFileName = URLEncoder.encode(fileName + "-" + LocalDateTime.now().format(formatter) + ".xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename=" + encodedFileName);
        response.setHeader("Content-Transfer-Encoding", "binary");

        try (OutputStream out = response.getOutputStream()) {
            workbook.write(out);
            out.flush();
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }

    /**
     * 通用导出Excel
     *
     * @param response      响应对象
     * @param fileName      文件名
     * @param sheetName     sheet名称
     * @param dataList      数据
     * @param headers       标题
     * @param fields        字段
     * @param isLock        是否锁定(禁止编辑)
     * @param ignoreIndexes 忽略锁定列的列索引 (isLock时可编辑的列)
     */
    public static  <T>  void exportToExcel(
            HttpServletResponse response,
            String fileName,
            String sheetName,
            List<T> dataList,
            String[] headers,
            String[] fields,
            List<Integer> ignoreIndexes,
            boolean isLock
    ) throws IOException {
        int rowAccessWindowSize = 500;
        SXSSFWorkbook workbook = new SXSSFWorkbook(rowAccessWindowSize);
        workbook.setCompressTempFiles(true);
        Sheet sheet = workbook.createSheet(sheetName);
        sheet.createFreezePane(0, 1);

        // 标题行样式
        CellStyle headerStyle = createHeaderStyle(workbook);

        // 创建标题行
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 25 * 256);
        }

        // 数据行样式 - 所有单元格都锁定（只读）
        CellStyle lockedCellStyle = workbook.createCellStyle();
        lockedCellStyle.setLocked(true);

        // 可编辑样式（解锁）
        CellStyle unlockedCellStyle = workbook.createCellStyle();
        unlockedCellStyle.setLocked(false);

        // 摘出Cell Style，按需调用，防止异常：The maximum number of Cell Styles was exceeded. You can define up to 64000 style in a .xlsx Workbook
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(workbook.getCreationHelper()
                .createDataFormat().getFormat("yyyy-MM-dd HH:mm:ss"));
        dateStyle.setAlignment(HorizontalAlignment.LEFT);

        int rowNum = 1;
        for (T data : dataList) {
            Row row = sheet.createRow(rowNum++);

            for (int col = 0; col < fields.length; col++) {
                Object value = getFieldValue(data, fields[col]);
                Cell cell = row.createCell(col);

                // 设置单元格值
                if (value != null) {
                    if (value instanceof Date) {
                        cell.setCellStyle(dateStyle);
                        cell.setCellValue((Date) value);
                    } else if (value instanceof LocalDateTime) {
                        cell.setCellStyle(dateStyle);
                        cell.setCellValue(LocalDateTime.class.cast(value));
                    } else if (value instanceof Number) {
                        cell.setCellValue(((Number) value).doubleValue());
                        cell.setCellStyle(lockedCellStyle);
                    } else if (value instanceof Boolean) {
                        cell.setCellValue((Boolean) value);
                        cell.setCellStyle(lockedCellStyle);
                    } else {
                        cell.setCellValue(value.toString());
                        cell.setCellStyle(lockedCellStyle);
                    }
                } else {
                    cell.setCellValue("");
                    cell.setCellStyle(lockedCellStyle);
                }

                if (ignoreIndexes != null && ignoreIndexes.contains(col)) {
                    cell.setCellStyle(unlockedCellStyle);
                }

            }
        }

        // 设置自动筛选
        if (dataList.size() > 0) {
            sheet.setAutoFilter(new CellRangeAddress(0, dataList.size(), 0, headers.length - 1));
        }

        // 保护工作表（只读）
        if (isLock) {
            sheet.protectSheet("lock123");
            ((SXSSFSheet) sheet).lockAutoFilter(false);   // 保留筛选功能
            ((SXSSFSheet) sheet).lockFormatCells( false);   // 允许设置单元格格式
            ((SXSSFSheet) sheet).lockFormatColumns( false); // 允许调整列宽
            ((SXSSFSheet) sheet).lockFormatRows( false);    // 允许调整行高

            ((SXSSFSheet) sheet).enableLocking();
        }

        // 响应头
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String encodedFileName = URLEncoder.encode(fileName + "-" + LocalDateTime.now().format(formatter) + ".xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename=" + encodedFileName);
        response.setHeader("Content-Transfer-Encoding", "binary");

        try (OutputStream out = response.getOutputStream()) {
            workbook.write(out);
            out.flush();
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }

    /**
     * 导出Excel方法
     * @param response          响应对象
     * @param fileName          文件名
     * @param sheetName         sheet名称
     * @param dataList          数据
     * @param headers           标题
     * @param fields            字段
     * @param isLock            是否锁定(禁止编辑)
     * @param ignoreIndexes     忽略锁定列的列索引 (isLock时可编辑的列)
     * @param dropdownIndexes   下拉列索引
     * @param language          语言
     * */
    public static  <T>  void exportToExcel(
            HttpServletResponse response,
            String fileName,
            String sheetName,
            List<T> dataList,
            String[] headers,
            String[] fields,
            List<Integer> ignoreIndexes,
            boolean isLock,
            List<Integer> dropdownIndexes,
            String language
    ) throws IOException {
        int rowAccessWindowSize = 500;
        SXSSFWorkbook workbook = new SXSSFWorkbook(rowAccessWindowSize);
        workbook.setCompressTempFiles(true);
        Sheet sheet = workbook.createSheet(sheetName);
        sheet.createFreezePane(0, 1);

        // 标题行样式
        CellStyle headerStyle = createHeaderStyle(workbook);

        // 创建标题行
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 25 * 256);
        }

        // ========== 特殊化 start ：在写入数据前设置下拉框 ==========
        // 涉及多种options 后期改造传入
        if (dropdownIndexes != null && !dropdownIndexes.isEmpty()) {
            String[] options;
            String[] errorBox;
            if ("EN".equalsIgnoreCase(language)) {
                options = new String[]{"keep", "remove"};
                errorBox = new String[]{"Input error", "Please select a valid value from the drop-down list"};
            } else {
                options = new String[]{"保留", "移除"};
                errorBox = new String[]{"输入错误", "请从下拉列表中选择有效的值"};
            }

            DataValidationHelper helper = sheet.getDataValidationHelper();
            DataValidationConstraint constraint = helper.createExplicitListConstraint(options);

            // 下拉范围：第2行(索引1) 到 数据最后一行；空数据时至少预留100行模板空间
            int firstDataRow = 1;
            int lastDataRow = Math.max(dataList.size(), 100);

            for (Integer colIndex : dropdownIndexes) {
                if (colIndex == null || colIndex < 0 || colIndex >= headers.length) {
                    continue;
                }
                CellRangeAddressList addressList = new CellRangeAddressList(
                        firstDataRow, lastDataRow, colIndex, colIndex
                );
                DataValidation validation = helper.createValidation(constraint, addressList);
                validation.setSuppressDropDownArrow(true);          // 显示下拉箭头
                validation.setShowErrorBox(true);                    // 非法输入时拦截
                validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
                validation.createErrorBox(errorBox[0], errorBox[1]);
                sheet.addValidationData(validation);
            }
        }
        // ========== 特殊化 end ==========

        // 数据行样式 - 所有单元格都锁定（只读）
        CellStyle lockedCellStyle = workbook.createCellStyle();
        lockedCellStyle.setLocked(true);

        // 可编辑样式（解锁）
        CellStyle unlockedCellStyle = workbook.createCellStyle();
        unlockedCellStyle.setLocked(false);

        // 摘出Cell Style，按需调用，防止异常：The maximum number of Cell Styles was exceeded. You can define up to 64000 style in a .xlsx Workbook
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(workbook.getCreationHelper()
                .createDataFormat().getFormat("yyyy-MM-dd HH:mm:ss"));
        dateStyle.setAlignment(HorizontalAlignment.LEFT);

        int rowNum = 1;
        for (T data : dataList) {
            Row row = sheet.createRow(rowNum++);

            for (int col = 0; col < fields.length; col++) {
                Object value = getFieldValue(data, fields[col]);
                Cell cell = row.createCell(col);

                // 设置单元格值
                if (value != null) {
                    if (value instanceof Date) {
                        cell.setCellStyle(dateStyle);
                        cell.setCellValue((Date) value);
                    } else if (value instanceof LocalDateTime) {
                        cell.setCellStyle(dateStyle);
                        cell.setCellValue(LocalDateTime.class.cast(value));
                    } else if (value instanceof Number) {
                        cell.setCellValue(((Number) value).doubleValue());
                        cell.setCellStyle(lockedCellStyle);
                    } else if (value instanceof Boolean) {
                        cell.setCellValue((Boolean) value);
                        cell.setCellStyle(lockedCellStyle);
                    } else {
                        cell.setCellValue(value.toString());
                        cell.setCellStyle(lockedCellStyle);
                    }
                } else {
                    cell.setCellValue("");
                    cell.setCellStyle(lockedCellStyle);
                }

                if (ignoreIndexes != null && ignoreIndexes.contains(col)) {
                    cell.setCellStyle(unlockedCellStyle);
                }

            }
        }

        // 设置自动筛选
        if (dataList.size() > 0) {
            sheet.setAutoFilter(new CellRangeAddress(0, dataList.size(), 0, headers.length - 1));
        }

        // 保护工作表（只读）
        if (isLock) {
            sheet.protectSheet("lock123");
            ((SXSSFSheet) sheet).lockAutoFilter(false);   // 保留筛选功能
            ((SXSSFSheet) sheet).lockFormatCells( false);   // 允许设置单元格格式
            ((SXSSFSheet) sheet).lockFormatColumns( false); // 允许调整列宽
            ((SXSSFSheet) sheet).lockFormatRows( false);    // 允许调整行高

            ((SXSSFSheet) sheet).enableLocking();
        }

        // 响应头
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String encodedFileName = URLEncoder.encode(fileName + "-" + LocalDateTime.now().format(formatter) + ".xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename=" + encodedFileName);
        response.setHeader("Content-Transfer-Encoding", "binary");

        try (OutputStream out = response.getOutputStream()) {
            workbook.write(out);
            out.flush();
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }


    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();

        // 字体设置
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);

        // 背景色
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // 边框
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setBorderTop(BorderStyle.MEDIUM);
        style.setBorderRight(BorderStyle.MEDIUM);
        style.setBorderLeft(BorderStyle.MEDIUM);
        style.setBottomBorderColor(IndexedColors.BLUE_GREY.getIndex());
        style.setTopBorderColor(IndexedColors.BLUE_GREY.getIndex());
        style.setRightBorderColor(IndexedColors.BLUE_GREY.getIndex());
        style.setLeftBorderColor(IndexedColors.BLUE_GREY.getIndex());

        // 对齐方式
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        return style;
    }

    private static Object getFieldValue(Object obj, String fieldName) {
        try {
            // 支持嵌套属性（如：user.name）
            if (fieldName.contains(".")) {
                String[] parts = fieldName.split("\\.");
                Object value = obj;
                for (String part : parts) {
                    if (value == null) break;
                    Field field = value.getClass().getDeclaredField(part);
                    field.setAccessible(true);
                    value = field.get(value);
                }
                return value;
            }

            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            log.warn("获取字段值失败: {}", fieldName, e);
            return null;
        }
    }


    public static <T> ExcelFileBean createExcel(
            String fileName,
            String sheetName,
            List<T> dataList,
            String[] headers,
            String[] fields,
            List<Integer> ignoreIndexes,
            boolean isLock
    ) throws IOException {
        int rowAccessWindowSize = 500;
        SXSSFWorkbook workbook = new SXSSFWorkbook(rowAccessWindowSize);
        workbook.setCompressTempFiles(true);
        Sheet sheet = workbook.createSheet(sheetName);
        sheet.createFreezePane(0, 1);

        // 标题行样式
        CellStyle headerStyle = createHeaderStyle(workbook);

        // 创建标题行
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 30 * 256);
        }

        // 数据行样式 - 所有单元格都锁定（只读）
        CellStyle lockedCellStyle = workbook.createCellStyle();
        lockedCellStyle.setLocked(true);

        // 可编辑样式（解锁）
        CellStyle unlockedCellStyle = workbook.createCellStyle();
        unlockedCellStyle.setLocked(false);

        // 摘出Cell Style，按需调用，防止异常：The maximum number of Cell Styles was exceeded. You can define up to 64000 style in a .xlsx Workbook
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(workbook.getCreationHelper()
                .createDataFormat().getFormat("yyyy-MM-dd HH:mm:ss"));
        dateStyle.setAlignment(HorizontalAlignment.LEFT);

        int rowNum = 1;
        for (T data : dataList) {
            Row row = sheet.createRow(rowNum++);

            for (int col = 0; col < fields.length; col++) {
                Object value = getFieldValue(data, fields[col]);
                Cell cell = row.createCell(col);

                // 设置单元格值
                if (value != null) {
                    if (value instanceof Date) {
                        cell.setCellStyle(dateStyle);
                        cell.setCellValue((Date) value);
                    } else if (value instanceof LocalDateTime) {
                        cell.setCellStyle(dateStyle);
                        cell.setCellValue(LocalDateTime.class.cast(value));
                    } else if (value instanceof Number) {
                        cell.setCellValue(((Number) value).doubleValue());
                        cell.setCellStyle(lockedCellStyle);
                    } else if (value instanceof Boolean) {
                        cell.setCellValue((Boolean) value);
                        cell.setCellStyle(lockedCellStyle);
                    } else {
                        cell.setCellValue(value.toString());
                        cell.setCellStyle(lockedCellStyle);
                    }
                } else {
                    cell.setCellValue("");
                    cell.setCellStyle(lockedCellStyle);
                }

                if (ignoreIndexes != null && ignoreIndexes.contains(col)) {
                    cell.setCellStyle(unlockedCellStyle);
                }

            }
        }

        // 保护工作表（只读）
        if (isLock) {
            sheet.protectSheet("lock123");
            ((SXSSFSheet) sheet).lockAutoFilter(false);
            ((SXSSFSheet) sheet).lockFormatCells(false);
            ((SXSSFSheet) sheet).lockFormatColumns(false);
            ((SXSSFSheet) sheet).lockFormatRows(false);
            ((SXSSFSheet) sheet).enableLocking();
        }

        // 写入内存字节流
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            workbook.write(baos);
            baos.flush();
        } finally {
            workbook.dispose();
            workbook.close();
        }

        byte[] fileBytes = baos.toByteArray();

        // 组装返回实体
        ExcelFileBean info = new ExcelFileBean();
        info.setFileName(fileName + ".xlsx");
        info.setFileSize((long) fileBytes.length);
        info.setMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        info.setFileContent(fileBytes);

        return info;
    }

}