package com.lenovo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lenovo.bean.ExcelFileBean;
import com.lenovo.entity.FinalReviewExcelFile;
import com.lenovo.entity.UserAccessReview;
import com.lenovo.mapper.FinalReviewExcelFileMapper;
import com.lenovo.mapper.UserAccessReviewMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static com.lenovo.util.ExcelUtil.createExcel;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PingController {

    @GetMapping("/ping")
    public String ping() {
        return "success";
    }

    @GetMapping("/ping2")
    public String ping2() {
        return "success";
    }

    final UserAccessReviewMapper userAccessReviewMapper;
    final FinalReviewExcelFileMapper finalReviewExcelFileMapper;
    /**
     * 批量生成Excel文件
     * @return
     * @throws IOException
     */
    @GetMapping("/ping3")
    @Transactional
    public ResponseEntity ping3(
            String itcode
    ) throws IOException {
        String normalizedItCode = itcode == null ? null : itcode.trim().toLowerCase(Locale.ROOT);
        // itCode 只传itCode 生成对应的文件
        // type: 0 find, 1 resave
        List<UserAccessReview> distinctUserGroup = userAccessReviewMapper.getDistinctUserGroup(normalizedItCode);
        Map<String, List<UserAccessReview>> userGroup = distinctUserGroup.stream().collect(
                Collectors.groupingBy(review -> review.getItCodeOfUser().trim().toLowerCase(Locale.ROOT))
        );
        if (normalizedItCode != null) {
            finalReviewExcelFileMapper.delete(new QueryWrapper<FinalReviewExcelFile>()
                    .apply("LOWER(TRIM(it_code)) = {0}", normalizedItCode));
        } else {
            finalReviewExcelFileMapper.clearFinalReviewExcelFile();
        }


        String sheetName = "Review Decision";

        String[] fields = new String[]{
                "cmdbId", "appName", "itCodeOfUser", "systemRole", "roleDescription",
                "lineManager", "lineManagersReviewDecision", "lineManagerReviewTime",
                "bpo", "bpoReviewDecision", "bpoReviewTime",
                "finalReviewDecision", "finalReviewResultReason", "finalReviewTime"
        };
        String[] headers = new String[]{
                "App ID", "App Name", "User IT Code", "User Role Name", "Role Description",
                "Line Manager Itcode", "Line Manager Review Decision", "Line Manager Review Date",
                "BPO Itcode", "BPO Review Decision", "BPO Review Date",
                "Final Review Decision", "Final Review Result Reason", "Final Review Date"
        };
        userGroup.forEach((userItCode, entities) -> {
            log.info("用户 " + userItCode + " 有 " + entities.size() + " 条记录");
            try {
                ExcelFileBean excel = createExcel(userItCode, sheetName, entities, headers, fields, null, false);
                // 复制到数据库实体
                FinalReviewExcelFile dbFile = new FinalReviewExcelFile();
                BeanUtils.copyProperties(excel, dbFile);
                dbFile.setItCode(userItCode.trim().toLowerCase(Locale.ROOT));
                dbFile.setCreateBy("system");
                finalReviewExcelFileMapper.insert(dbFile);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        if (normalizedItCode == null) {
            try {
                String all = "remove_all_user";
                ExcelFileBean excel = createExcel(all, sheetName, distinctUserGroup, headers, fields, null, false);
                // 复制到数据库实体
                FinalReviewExcelFile dbFile = new FinalReviewExcelFile();
                BeanUtils.copyProperties(excel, dbFile);
                dbFile.setItCode(all);
                dbFile.setCreateBy("system");
                finalReviewExcelFileMapper.insert(dbFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        log.info("[ping3] total of {} data entries, matched {} users", distinctUserGroup.size(), userGroup.size());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "success",
                "total", distinctUserGroup.size(),
                "users", userGroup.size()
                ));
    }

}
