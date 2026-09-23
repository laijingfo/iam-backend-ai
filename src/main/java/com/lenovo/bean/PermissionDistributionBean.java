package com.lenovo.bean;

import lombok.Data;

/**
 * Permission distribution data used by the NA dashboard.
 */
public final class PermissionDistributionBean {

    private PermissionDistributionBean() {
    }

    @Data
    public static class Top10 {
        private String applicationName;
        private Long cocUsers;
        private Long nonCocUsers;
    }

    @Data
    public static class Detail {
        private String cmdbId;
        private String applicationName;
        private Long totalUsers;
        private Long totalPermissions;
        private Long cocUsers;
        private Long cocAssignedPermissions;
        private Long nonCocUsers;
        private Long nonCocAssignedPermissions;
        private Long invalidUsers;
        private Long invalidUsersAssignedPermissions;
        private String cocUserPercentage;
        private String cocPermissionPercentage;

        public void setCocUserPercentage(String cocUserPercentage) {
            this.cocUserPercentage = appendPercentageSign(cocUserPercentage);
        }

        public void setCocPermissionPercentage(String cocPermissionPercentage) {
            this.cocPermissionPercentage = appendPercentageSign(cocPermissionPercentage);
        }

        private static String appendPercentageSign(String value) {
            if (value == null || value.endsWith("%")) {
                return value;
            }
            return value + "%";
        }
    }
}
