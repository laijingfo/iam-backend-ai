ALTER TABLE user_access_review
    ADD COLUMN user_coc_type varchar(20),
    ADD COLUMN bpo_coc_type varchar(20);

COMMENT ON COLUMN user_access_review.user_coc_type IS
    'NA policy user CoC flag: Y or N; NULL means unknown, unavailable, or not applicable';
COMMENT ON COLUMN user_access_review.bpo_coc_type IS
    'NA policy BPO CoC flag: Y or N; populated only for one BPO; NULL means multiple BPOs, unknown, unavailable, or not applicable';

ALTER TABLE user_access_review_history
    ADD COLUMN user_coc_type varchar(20),
    ADD COLUMN bpo_coc_type varchar(20);

COMMENT ON COLUMN user_access_review_history.user_coc_type IS
    'Archived NA policy user CoC flag snapshot: Y or N; NULL means unknown, unavailable, or not applicable';
COMMENT ON COLUMN user_access_review_history.bpo_coc_type IS
    'Archived NA policy BPO CoC flag snapshot: Y or N; populated only for one BPO; NULL means multiple BPOs, unknown, unavailable, or not applicable';

ALTER TABLE user_access_review_alert
    ADD COLUMN user_coc_type varchar(20),
    ADD COLUMN bpo_coc_type varchar(20);

COMMENT ON COLUMN user_access_review_alert.user_coc_type IS
    'NA policy user CoC flag snapshot at alert creation: Y or N; NULL means unknown, unavailable, or not applicable';
COMMENT ON COLUMN user_access_review_alert.bpo_coc_type IS
    'NA policy BPO CoC flag snapshot at alert creation: Y or N; populated only for one BPO; NULL means multiple BPOs, unknown, unavailable, or not applicable';

ALTER TABLE user_access_review_alert_history
    ADD COLUMN user_coc_type varchar(20),
    ADD COLUMN bpo_coc_type varchar(20);

COMMENT ON COLUMN user_access_review_alert_history.user_coc_type IS
    'Archived NA policy user CoC flag snapshot: Y or N; NULL means unknown, unavailable, or not applicable';
COMMENT ON COLUMN user_access_review_alert_history.bpo_coc_type IS
    'Archived NA policy BPO CoC flag snapshot: Y or N; populated only for one BPO; NULL means multiple BPOs, unknown, unavailable, or not applicable';
-- 约束
--ALTER TABLE user_access_review
--    ADD CONSTRAINT chk_uar_user_coc_type
--        CHECK (user_coc_type IS NULL OR user_coc_type IN ('Y', 'N')),
--    ADD CONSTRAINT chk_uar_bpo_coc_type
--        CHECK (bpo_coc_type IS NULL OR bpo_coc_type IN ('Y', 'N'));
--
--ALTER TABLE user_access_review_history
--    ADD CONSTRAINT chk_uar_history_user_coc_type
--        CHECK (user_coc_type IS NULL OR user_coc_type IN ('Y', 'N')),
--    ADD CONSTRAINT chk_uar_history_bpo_coc_type
--        CHECK (bpo_coc_type IS NULL OR bpo_coc_type IN ('Y', 'N'));
--
--ALTER TABLE user_access_review_alert
--    ADD CONSTRAINT chk_uar_alert_user_coc_type
--        CHECK (user_coc_type IS NULL OR user_coc_type IN ('Y', 'N')),
--    ADD CONSTRAINT chk_uar_alert_bpo_coc_type
--        CHECK (bpo_coc_type IS NULL OR bpo_coc_type IN ('Y', 'N'));
--
--ALTER TABLE user_access_review_alert_history
--    ADD CONSTRAINT chk_uar_alert_history_user_coc_type
--        CHECK (user_coc_type IS NULL OR user_coc_type IN ('Y', 'N')),
--    ADD CONSTRAINT chk_uar_alert_history_bpo_coc_type
--        CHECK (bpo_coc_type IS NULL OR bpo_coc_type IN ('Y', 'N'));
