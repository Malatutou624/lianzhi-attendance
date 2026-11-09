-- V5 AI 助手会话表（之前漏了，导致 Docker 环境下 AI 助手/知识库全部报错）

CREATE TABLE IF NOT EXISTS `ai_session` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `user_id`     BIGINT       NOT NULL COMMENT '用户ID（admin=sys_user.id, h5=h5_account.id）',
  `user_type`   VARCHAR(16)  NOT NULL COMMENT 'admin / h5',
  `title`       VARCHAR(128) DEFAULT NULL COMMENT '会话标题（首问自动截取）',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user (`user_id`, `user_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 会话';

CREATE TABLE IF NOT EXISTS `ai_message` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `session_id`  BIGINT       NOT NULL COMMENT '所属会话',
  `role`        VARCHAR(16)  NOT NULL COMMENT 'user / assistant',
  `content`     MEDIUMTEXT   NOT NULL COMMENT '消息正文',
  `sources`     JSON         DEFAULT NULL COMMENT '来源引用数组 [{source, snippet}]',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_session (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 会话消息';
