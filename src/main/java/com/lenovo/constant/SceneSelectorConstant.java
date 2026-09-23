package com.lenovo.constant;

import com.lenovo.security.exception.BadRequestException;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * 下拉选择器配置。前端只传场景和字段编码，真实字段名及业务 SQL 始终由后端维护。
 */
public final class SceneSelectorConstant {

    private SceneSelectorConstant() {
    }

    public enum Field {
        CMDB_ID("cmdbId", "cmdb_id"),
        APP_NAME("appName", "application_name"),
        OPERATION_DOMAIN("operationDomain", "operation_owner_domain"),
        OPERATION_TOWER("operationTower", "operation_owner_tower"),
        OPERATION_OWNER("operationOwner", "operation_owner"),
        OPERATION_FOCAL("operationFocal", "operation_focal");

        private final String code;
        private final String column;

        Field(String code, String column) {
            this.code = code;
            this.column = column;
        }

        public String getColumn() {
            return column;
        }

        public static Field fromCode(String code) {
            return Arrays.stream(values())
                    .filter(item -> item.code.equalsIgnoreCase(code))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Unsupported fields: " + code));
        }
    }

    public enum Scene {
        NORMAL("normal",
                "SELECT DISTINCT cmdb_id FROM user_access_review",
                EnumSet.of(Field.CMDB_ID, Field.APP_NAME)
        ),
        INTEGRATION_DATA("integrationData",
                "SELECT DISTINCT cmdb_id FROM its_application_access_data",
                EnumSet.of(Field.CMDB_ID, Field.APP_NAME),
                false
        ),
        ALERT_DASHBOARD("alertDashboard",
                "SELECT DISTINCT cmdb_id FROM user_access_review_alert",
                EnumSet.of(Field.CMDB_ID, Field.APP_NAME)
        ),
        ALERT_INVALID_BPO("alertInvalidBpo",
                "SELECT DISTINCT cmdb_id FROM user_access_review_alert WHERE alert_type IN (12, 32) AND alert_handle_status IN ('Pending', 'Send') ",
                EnumSet.of(Field.CMDB_ID, Field.APP_NAME, Field.OPERATION_OWNER, Field.OPERATION_FOCAL)
        ),
        DASHBOARD("dashboard",
                null,
                EnumSet.allOf(Field.class)
        ),
        NA_SENSITIVE_ACCESS_ALERT("naSensitiveAccessAlert",
                "SELECT DISTINCT cmdb_id FROM na_sensitive_access_alert",
                EnumSet.of(Field.CMDB_ID, Field.APP_NAME),
                false
        ),
        ;

        private final String code;
        private final String scopeSql;
        private final Set<Field> supportedFields;
        private final boolean filterOnlineApplications;

        Scene(String code, String scopeSql, Set<Field> supportedFields) {
            this(code, scopeSql, supportedFields, true);
        }

        Scene(String code, String scopeSql, Set<Field> supportedFields, boolean filterOnlineApplications) {
            this.code = code;
            this.scopeSql = scopeSql;
            this.supportedFields = supportedFields;
            this.filterOnlineApplications = filterOnlineApplications;
        }

        public String getScopeSql() {
            return scopeSql;
        }

        public boolean supports(Field field) {
            return supportedFields.contains(field);
        }

        public boolean shouldFilterOnlineApplications() {
            return filterOnlineApplications;
        }

        public static Scene fromCode(String code) {
            return Arrays.stream(values())
                    .filter(item -> item.code.equalsIgnoreCase(code))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Unsupported business scenarios: " + code));
        }
    }

    /**
     * 看板下拉选项的 SQL
     * currentDataCycle 已做校验，防止SQL注入
     */
    public static String dashboardScopeSql(boolean currentCycle, String currentDataCycle) {
        if (currentCycle) {
            return "SELECT DISTINCT cmdb_id FROM user_access_review";
        }
        return "SELECT DISTINCT cmdb_id FROM user_access_review_history WHERE maintenance_id = '"+ currentDataCycle + "'";
    }
}
