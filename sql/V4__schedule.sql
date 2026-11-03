-- V4 定时任务相关表
-- 1. 考勤月报（每月自动生成）
-- 2. 系统通知（补卡提醒、月报通知等）

CREATE TABLE IF NOT EXISTS `monthly_report` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `report_month` VARCHAR(7) NOT NULL COMMENT '月份 YYYY-MM，如 2026-11',
  `emp_id` BIGINT NOT NULL COMMENT '员工 ID',
  `emp_name` VARCHAR(64) DEFAULT NULL,
  `dept` VARCHAR(64) DEFAULT NULL COMMENT '部门',
  `venue_id` BIGINT DEFAULT NULL,
  `venue_name` VARCHAR(64) DEFAULT NULL,
  `work_days` INT DEFAULT 0 COMMENT '出勤天数',
  `late_count` INT DEFAULT 0 COMMENT '迟到次数',
  `early_count` INT DEFAULT 0 COMMENT '早退次数',
  `absent_count` INT DEFAULT 0 COMMENT '旷工天数',
  `leave_count` INT DEFAULT 0 COMMENT '请假天数',
  `overtime_hours` DECIMAL(8,2) DEFAULT 0 COMMENT '加班小时数',
  `total_hours` DECIMAL(10,2) DEFAULT 0 COMMENT '累计工时',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_month_emp` (`report_month`, `emp_id`),
  KEY `idx_emp` (`emp_id`),
  KEY `idx_month` (`report_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤月报';

CREATE TABLE IF NOT EXISTS `system_notice` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `user_id` BIGINT NOT NULL COMMENT '目标用户 ID',
  `user_type` VARCHAR(16) NOT NULL DEFAULT 'h5' COMMENT 'admin / h5 / all',
  `type` VARCHAR(32) NOT NULL COMMENT 'MAKEUP_REMIND / MONTHLY_REPORT / SYSTEM',
  `title` VARCHAR(128) NOT NULL,
  `content` TEXT COMMENT '通知正文（HTML 可选）',
  `related_id` BIGINT DEFAULT NULL COMMENT '关联业务 ID（如月报 ID、审批 ID）',
  `is_read` TINYINT(1) DEFAULT 0 COMMENT '0=未读, 1=已读',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_user` (`user_id`, `is_read`),
  KEY `idx_create` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统通知/站内信';
