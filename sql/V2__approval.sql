-- ============================================================
-- 移动端审批功能 - 数据库迁移脚本
-- 执行方式：在 Navicat 中逐条执行
-- ============================================================

-- 1. employee 表添加主管字段
ALTER TABLE employee ADD COLUMN leader_id BIGINT DEFAULT NULL COMMENT '直属主管员工ID' AFTER status;
ALTER TABLE employee ADD COLUMN role TINYINT NOT NULL DEFAULT 1 COMMENT '1普通员工 2部门主管' AFTER leader_id;

-- 2. 审批记录表
CREATE TABLE approval_record (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    emp_id        BIGINT       NOT NULL                COMMENT '申请人员工ID',
    type          VARCHAR(20)  NOT NULL                COMMENT 'leave事假/sick病假/annual年假/overtime加班/compensate调休/makeup补卡/resign离职',
    start_time    DATETIME     DEFAULT NULL            COMMENT '开始时间（离职类型为空）',
    end_time      DATETIME     DEFAULT NULL            COMMENT '结束时间（离职类型为空）',
    duration_hours DECIMAL(5,2) DEFAULT NULL           COMMENT '时长（小时，自动计算或手动填）',
    reason        TEXT         DEFAULT NULL            COMMENT '事由/原因',
    attachments   VARCHAR(500) DEFAULT NULL            COMMENT '附件URL，多个逗号分隔',
    leader_id     BIGINT       DEFAULT NULL            COMMENT '直属主管ID',
    status        VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending待审批/approved已通过/rejected已驳回/cancelled已撤回',
    reject_reason TEXT         DEFAULT NULL            COMMENT '驳回意见',
    approver_id   BIGINT       DEFAULT NULL            COMMENT '审批人（主管）ID',
    approve_time  DATETIME     DEFAULT NULL            COMMENT '审批时间',
    resign_reason TEXT         DEFAULT NULL            COMMENT '离职原因（仅离职类型使用）',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_emp_status (emp_id, status),
    INDEX idx_leader_status (leader_id, status),
    INDEX idx_type (type)
) COMMENT '审批记录表';
