-- UAR 周期设置：增加默认起止日期与权限审核范围。
ALTER TABLE uar_cycle_setting
    ADD COLUMN IF NOT EXISTS default_cycle_start_date DATE,
    ADD COLUMN IF NOT EXISTS default_cycle_end_date DATE,
    ADD COLUMN IF NOT EXISTS access_review_scope VARCHAR(30);

COMMENT ON COLUMN uar_cycle_setting.default_cycle_start_date
    IS 'UAR周期默认开始日期';
COMMENT ON COLUMN uar_cycle_setting.default_cycle_end_date
    IS 'UAR周期默认结束日期';
COMMENT ON COLUMN uar_cycle_setting.access_review_scope
    IS '权限审核范围：ALL_ACCESS-全部权限，SENSITIVE_ACCESS-仅敏感权限';

UPDATE uar_cycle_setting
SET access_review_scope = 'ALL_ACCESS'
WHERE access_review_scope IS NULL;

ALTER TABLE uar_cycle_setting
    ALTER COLUMN access_review_scope SET DEFAULT 'ALL_ACCESS',
    ALTER COLUMN access_review_scope SET NOT NULL;

ALTER TABLE uar_cycle_setting
    DROP CONSTRAINT IF EXISTS ck_uar_cycle_setting_access_review_scope;
ALTER TABLE uar_cycle_setting
    ADD CONSTRAINT ck_uar_cycle_setting_access_review_scope
        CHECK (access_review_scope IN ('ALL_ACCESS', 'SENSITIVE_ACCESS'));

-- 应用周期维护：保存开启周期时选择的权限审核范围。
ALTER TABLE uar_cycle_maintenance
    ADD COLUMN IF NOT EXISTS access_review_scope VARCHAR(30);

COMMENT ON COLUMN uar_cycle_maintenance.access_review_scope
    IS '权限审核范围：ALL_ACCESS-全部权限，SENSITIVE_ACCESS-仅敏感权限';

UPDATE uar_cycle_maintenance
SET access_review_scope = 'ALL_ACCESS'
WHERE access_review_scope IS NULL;

ALTER TABLE uar_cycle_maintenance
    ALTER COLUMN access_review_scope SET DEFAULT 'ALL_ACCESS',
    ALTER COLUMN access_review_scope SET NOT NULL;

ALTER TABLE uar_cycle_maintenance
    DROP CONSTRAINT IF EXISTS ck_uar_cycle_maintenance_access_review_scope;
ALTER TABLE uar_cycle_maintenance
    ADD CONSTRAINT ck_uar_cycle_maintenance_access_review_scope
        CHECK (access_review_scope IN ('ALL_ACCESS', 'SENSITIVE_ACCESS'));

-- 归档表同步保存权限审核范围。
ALTER TABLE uar_cycle_maintenance_history
    ADD COLUMN IF NOT EXISTS access_review_scope VARCHAR(30);

COMMENT ON COLUMN uar_cycle_maintenance_history.access_review_scope
    IS '权限审核范围：ALL_ACCESS-全部权限，SENSITIVE_ACCESS-仅敏感权限';

UPDATE uar_cycle_maintenance_history
SET access_review_scope = 'ALL_ACCESS'
WHERE access_review_scope IS NULL;

ALTER TABLE uar_cycle_maintenance_history
    ALTER COLUMN access_review_scope SET DEFAULT 'ALL_ACCESS',
    ALTER COLUMN access_review_scope SET NOT NULL;

ALTER TABLE uar_cycle_maintenance_history
    DROP CONSTRAINT IF EXISTS ck_uar_cycle_maintenance_history_access_review_scope;
ALTER TABLE uar_cycle_maintenance_history
    ADD CONSTRAINT ck_uar_cycle_maintenance_history_access_review_scope
        CHECK (access_review_scope IN ('ALL_ACCESS', 'SENSITIVE_ACCESS'));
