package com.lenovo.service.impl;

import com.lenovo.bean.UseAccessReviewBean;
import com.lenovo.entity.UserAccessReview;
import com.lenovo.mapper.BPOMapper;
import com.lenovo.mapper.LineMangerReviewMapper;
import com.lenovo.service.RegionalUarPolicyService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

import static com.lenovo.util.ExcelUtil.exportReviewToExcel;

/**
 * The type NA regional UAR policy service.
 */
@Service
@Profile("prod-na")
@RequiredArgsConstructor
public class NaRegionalUarPolicyService implements RegionalUarPolicyService {

    private final LineMangerReviewMapper lineMangerReviewMapper;
    private final BPOMapper bpoMapper;

    /**
     * Export line manager review
     *
     * @param response the response
     * @param query    the query
     * @param language the language
     * @throws IOException the io exception
     */
    @Override
    public void exportLineManagerReview(
            HttpServletResponse response,
            UseAccessReviewBean query,
            String language
    ) throws IOException {
        List<UserAccessReview> reviews = lineMangerReviewMapper.getAllLineManagerReview(query, language);
        formatValuesForExport(reviews);
        boolean chinese = "CN".equalsIgnoreCase(language);
        String[] headers = chinese
                ? new String[]{
                "序列号", "应用编号", "应用名称", "用户 ITcode", "用户 CoC 类型", "用户名", "用户角色",
                "角色描述", "用户账号", "角色分类", "直属经理",
                "您的审核状态", "您的审核结果"
        }
                : new String[]{
                "Sequence Number", "Application ID", "System Name", "User ITcode", "User CoC Type", "User Full Name", "User Role Name",
                "Role Description", "User Account", "Role Classification", "Line Manager",
                "Your Review Status", "Your Review Result"
        };
        String[] fields = {
                "sequenceNumber", "cmdbId", "appName", "itCodeOfUser", "userCocType", "userRealName", "systemRole",
                "roleDescription", "userId", "accessLabel", "lineManager",
                "lineManagerReviewStatus", "lineManagersReviewDecision"
        };
        exportReviewToExcel(
                response,
                chinese ? "直属经理审核" : "Line_Manager_Review",
                chinese ? "直属经理审核" : "Line_Manager_Review",
                reviews,
                headers,
                fields,
                chinese
                        ? "为避免误操作修改，我们已锁定A列至L列。请在M列选择您的审核意见 (保留或移除)。"
                        : "Please note that columns A through L have been locked to prevent accidental modification."
                        + "Please select your review results (keep/remove) in Column M.",
                language
        );
    }

    /**
     * Export bpo review
     *
     * @param response the response
     * @param query    the query
     * @param language the language
     * @throws IOException the io exception
     */
    @Override
    public void exportBpoReview(
            HttpServletResponse response,
            UseAccessReviewBean query,
            String language
    ) throws IOException {
        List<UserAccessReview> reviews = bpoMapper.getAllBPOReview(query, language);
        formatValuesForExport(reviews);
        boolean chinese = "CN".equalsIgnoreCase(language);
        String[] headers = chinese
                ? new String[]{
                "序列号", "应用编号", "应用名称", "用户 ITcode", "用户名", "用户 CoC 类型", "用户角色",
                "角色描述", "国家/地区", "用户账号", "部门", "角色分类", "直属经理",
                "直属经理审核状态", "直属经理审核结果", "BPO", "BPO CoC 类型",
                "您的审核状态", "您的审核结果"
        }
                : new String[]{
                "Sequence Number", "Application ID", "System Name", "User ITcode", "User Full Name",
                "User CoC Type", "User Role Name", "Role Description", "Country/Region", "User Account",
                "Department", "Role Classification", "Line Manager",
                "Line Manager Review Status", "Line Manager Review Result", "BPO", "BPO CoC Type",
                "Your Review Status", "Your Review Result"
        };
        String[] fields = {
                "sequenceNumber", "cmdbId", "appName", "itCodeOfUser", "userRealName", "userCocType",
                "systemRole", "roleDescription", "country", "userId", "department", "accessLabel",
                "lineManager", "lineManagerReviewStatus",
                "lineManagersReviewDecision", "bpo", "bpoCocType", "bpoReviewStatus", "bpoReviewDecision"
        };
        exportReviewToExcel(
                response,
                chinese ? "BPO审核" : "BPO_Review",
                chinese ? "BPO审核" : "BPO_Review",
                reviews,
                headers,
                fields,
                chinese
                        ? "为避免误操作修改，我们已锁定A列至R列。请在S列选择您的审核意见 (保留或移除)。"
                        : "Please note that columns A through R have been locked to prevent accidental modification."
                        + "Please select your review results (keep/remove) in Column S.",
                language
        );
    }

    /**
     * Format the values for export
     *
     * @param reviews the reviews
     */
    private void formatValuesForExport(List<UserAccessReview> reviews) {
        reviews.forEach(review -> {
            review.setUserCocType(toCocDisplayValue(review.getUserCocType()));
            review.setBpoCocType(toCocDisplayValue(review.getBpoCocType()));
            review.setAccessLabel(formatAccessLabelForExport(review.getAccessLabel(), null));
        });
    }

    /**
     * Format the access label for export
     *
     * @param accessLabel the access label
     * @param language    the language
     * @return the string
     */
    @Override
    public String formatAccessLabelForExport(String accessLabel, String language) {
        if ("sensitive".equalsIgnoreCase(accessLabel)) {
            return "NA-Sensitive";
        }
        if ("standard".equalsIgnoreCase(accessLabel)) {
            return "Non-NA-Sensitive";
        }
        return null;
    }

    /**
     * To coc display value
     *
     * @param cocFlag the coc flag
     * @return the string
     */
    private String toCocDisplayValue(String cocFlag) {
        if ("Y".equalsIgnoreCase(cocFlag)) {
            return "COC";
        }
        if ("N".equalsIgnoreCase(cocFlag)) {
            return "non-COC";
        }
        return null;
    }
}
