-- ============================================================
-- 知识库模块 4 张表
-- ============================================================

-- 1. 分类
CREATE TABLE IF NOT EXISTS `kn_category` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `name`        VARCHAR(64)   NOT NULL COMMENT '分类名：考勤制度/请假规范/员工手册...',
  `description` VARCHAR(255)  DEFAULT NULL COMMENT '说明',
  `sort_order`  INT           NOT NULL DEFAULT 0,
  `active`      TINYINT(1)    NOT NULL DEFAULT 1,
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_active (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库分类';

-- 2. 文档
CREATE TABLE IF NOT EXISTS `kn_document` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `category_id` BIGINT        DEFAULT NULL COMMENT '分类ID',
  `name`        VARCHAR(255)  NOT NULL COMMENT '文档名',
  `file_name`   VARCHAR(512)  NOT NULL COMMENT '原始文件名',
  `file_path`   VARCHAR(512)  NOT NULL COMMENT '服务器存储路径',
  `file_size`   BIGINT        DEFAULT NULL,
  `format`      VARCHAR(16)   NOT NULL COMMENT 'pdf/docx/xlsx/pptx/txt/image/audio/video',
  `version`     INT           NOT NULL DEFAULT 1,
  `version_note` VARCHAR(255) DEFAULT NULL COMMENT '版本备注',
  `effective_at` DATETIME     DEFAULT NULL COMMENT '生效时间',
  `tags`        VARCHAR(512)  DEFAULT NULL COMMENT '逗号分隔标签',
  `status`      VARCHAR(16)   NOT NULL DEFAULT 'pending' COMMENT 'pending→parsing→chunking→embedding→ready/failed/deleted',
  `parse_error` VARCHAR(1024) DEFAULT NULL,
  `uploaded_by` VARCHAR(64)   DEFAULT NULL,
  `uploaded_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME      ON UPDATE CURRENT_TIMESTAMP,
  `active`      TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '停用后不再参与检索',
  INDEX idx_category (`category_id`),
  INDEX idx_status (`status`),
  INDEX idx_active (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档';

-- 3. 切片（Chunk）
CREATE TABLE IF NOT EXISTS `kn_chunk` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `doc_id`      BIGINT        NOT NULL COMMENT '所属文档',
  `index_no`    INT           NOT NULL COMMENT '切片序号',
  `title`       VARCHAR(255)  DEFAULT NULL COMMENT '切片标题（如章节名）',
  `content`     MEDIUMTEXT    NOT NULL COMMENT '切片文本',
  `source_ref`  VARCHAR(128)  DEFAULT NULL COMMENT '来源定位：页码/章节名/时间戳',
  `embedding`   LONGTEXT      DEFAULT NULL COMMENT '向量 JSON，如 "[0.12,-0.34,...]"',
  `alg`         VARCHAR(16)   DEFAULT NULL COMMENT 'embedding 算法: tfidf / dashscope',
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_doc (`doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识切片';

-- 4. 入库任务（异步）
CREATE TABLE IF NOT EXISTS `kn_task` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `doc_id`      BIGINT        NOT NULL,
  `stage`       VARCHAR(16)   NOT NULL DEFAULT 'queued' COMMENT 'queued→parsing→chunking→embedding→done/failed',
  `progress`    INT           NOT NULL DEFAULT 0 COMMENT '0-100',
  `message`     VARCHAR(512)  DEFAULT NULL COMMENT '当前步骤说明或错误信息',
  `retry_count` INT           NOT NULL DEFAULT 0,
  `canceled`    TINYINT(1)    NOT NULL DEFAULT 0,
  `started_at`  DATETIME      DEFAULT NULL,
  `finished_at` DATETIME      DEFAULT NULL,
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_doc (`doc_id`),
  INDEX idx_stage (`stage`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库入库任务';

-- 默认分类
INSERT IGNORE INTO kn_category (name, description, sort_order) VALUES
  ('考勤制度', '上下班时间、迟到早退处罚规则', 1),
  ('请假规范', '请假类型、流程、审批要求', 2),
  ('员工手册', '公司规章制度总则', 3),
  ('通知公告', '临时通知、重要公告', 4),
  ('加班与补卡', '加班申请、补卡流程规定', 5);
