-- ============================================================
-- 联智·智能考勤系统 数据库初始化脚本
-- 数据库：MySQL 8.x    字符集：utf8mb4
-- 使用方式：在 MySQL 中执行本文件全部内容
-- ============================================================

CREATE DATABASE IF NOT EXISTS lianzhi_attendance DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE lianzhi_attendance;

-- ------------------------------------------------------------
-- 1. 系统用户表：管理端登录账号
-- ------------------------------------------------------------
CREATE TABLE sys_user (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(32)  NOT NULL UNIQUE          COMMENT '登录账号',
    password    VARCHAR(64)  NOT NULL                 COMMENT '密码（MD5加密存储）',
    real_name   VARCHAR(32)  DEFAULT NULL             COMMENT '真实姓名',
    phone       VARCHAR(20)  DEFAULT NULL             COMMENT '手机号',
    email       VARCHAR(64)  DEFAULT NULL             COMMENT '邮箱',
    avatar      VARCHAR(255) DEFAULT NULL             COMMENT '头像URL',
    role        VARCHAR(16)  NOT NULL DEFAULT 'admin' COMMENT '角色：admin管理员',
    status      TINYINT      NOT NULL DEFAULT 1       COMMENT '1正常 0停用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '系统用户表';

-- ------------------------------------------------------------
-- 2. 办公场所表：公司/子公司/分部，支持层级
-- ------------------------------------------------------------
CREATE TABLE venue (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    name          VARCHAR(64)  NOT NULL               COMMENT '场所名称，如：北京总部',
    company_name  VARCHAR(64)  DEFAULT NULL           COMMENT '所属公司名称',
    parent_id     BIGINT       DEFAULT 0              COMMENT '上级场所ID，0=总部',
    address       VARCHAR(255) DEFAULT NULL           COMMENT '详细地址',
    longitude     DECIMAL(10,6) DEFAULT NULL          COMMENT '经度（地图采集）',
    latitude      DECIMAL(10,6) DEFAULT NULL          COMMENT '纬度',
    radius        INT          DEFAULT 300            COMMENT '允许打卡范围（米）',
    qrcode_param  VARCHAR(128) DEFAULT NULL           COMMENT '场所码参数（二维码内容）',
    status        TINYINT      NOT NULL DEFAULT 1     COMMENT '1启用 0停用',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '办公场所表';

-- ------------------------------------------------------------
-- 3. 员工表：H5 扫码采集后写入，含人脸信息
-- ------------------------------------------------------------
CREATE TABLE employee (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    emp_no        VARCHAR(32)  NOT NULL UNIQUE        COMMENT '工号',
    name          VARCHAR(32)  NOT NULL               COMMENT '姓名',
    gender        TINYINT      DEFAULT 1              COMMENT '1男 2女',
    phone         VARCHAR(20)  DEFAULT NULL           COMMENT '手机号',
    id_card       VARCHAR(18)  DEFAULT NULL           COMMENT '身份证号（人证核验用）',
    dept          VARCHAR(64)  DEFAULT NULL           COMMENT '部门',
    position      VARCHAR(64)  DEFAULT NULL           COMMENT '职位',
    venue_id      BIGINT       DEFAULT NULL           COMMENT '所属办公场所ID',
    face_photo    VARCHAR(255) DEFAULT NULL           COMMENT '人脸照片URL',
    face_feature  TEXT         DEFAULT NULL           COMMENT '人脸特征值（虹软SDK提取，Base64）',
    status        TINYINT      NOT NULL DEFAULT 1     COMMENT '1在职 2病假 3事假 0离职',
    role          TINYINT      NOT NULL DEFAULT 1     COMMENT '1普通员工 2部门主管',
    leader_id     BIGINT       DEFAULT NULL           COMMENT '直属主管员工ID',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '员工表';

-- ------------------------------------------------------------
-- 4. 打卡记录表：核心流水表，每次打卡一条
-- ------------------------------------------------------------
CREATE TABLE checkin_record (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    emp_id       BIGINT       NOT NULL                COMMENT '员工ID',
    venue_id     BIGINT       DEFAULT NULL            COMMENT '打卡场所ID',
    sign_in_time  DATETIME    DEFAULT NULL            COMMENT '签到时间（上班打卡）',
    sign_out_time DATETIME    DEFAULT NULL            COMMENT '签退时间（下班打卡）',
    checkin_type VARCHAR(16)  DEFAULT 'face'          COMMENT 'face人脸识别 / photo拍照',
    photo_url    VARCHAR(255) DEFAULT NULL            COMMENT '打卡现场照片URL（签到时拍照）',
    sign_out_photo VARCHAR(255) DEFAULT NULL          COMMENT '签退照片URL',
    similarity   DECIMAL(5,2) DEFAULT NULL            COMMENT '人脸比对相似度(0-100)',
    liveness     DECIMAL(5,2) DEFAULT NULL            COMMENT '活体检测分数(0-1)',
    longitude    DECIMAL(10,6) DEFAULT NULL           COMMENT '打卡经度',
    latitude     DECIMAL(10,6) DEFAULT NULL           COMMENT '打卡纬度',
    device_info  TEXT         DEFAULT NULL            COMMENT '设备信息(UserAgent，移动端UA可能很长)',
    status       VARCHAR(16)  DEFAULT 'normal'        COMMENT 'normal正常 / late迟到 / early早退',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_emp_time (emp_id, sign_in_time),
    INDEX idx_time (sign_in_time),
    INDEX idx_venue_time (venue_id, sign_in_time)
) COMMENT '打卡记录表';

-- ------------------------------------------------------------
-- 5. 考勤规则表：每个场所一条，用于判定迟到早退
-- ------------------------------------------------------------
CREATE TABLE attend_rule (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    venue_id     BIGINT NOT NULL                      COMMENT '场所ID',
    work_start   TIME    DEFAULT '09:00:00'           COMMENT '上班时间',
    work_end     TIME    DEFAULT '18:00:00'           COMMENT '下班时间',
    late_minutes INT     DEFAULT 10                   COMMENT '超过上班点多少分钟算迟到',
    UNIQUE KEY uk_venue (venue_id)
) COMMENT '考勤规则表';

-- ------------------------------------------------------------
-- 6. 每日统计表：Spring Task 定时任务每天生成
-- ------------------------------------------------------------
CREATE TABLE daily_stats (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    stat_date     DATE NOT NULL                       COMMENT '统计日期',
    venue_id      BIGINT NOT NULL                     COMMENT '场所ID',
    emp_total     INT  DEFAULT 0                      COMMENT '应到人数',
    checkin_total INT  DEFAULT 0                      COMMENT '实际打卡人数',
    late_total    INT  DEFAULT 0                      COMMENT '迟到人数',
    absent_total  INT  DEFAULT 0                      COMMENT '缺勤人数',
    create_time   DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_date_venue (stat_date, venue_id)
) COMMENT '每日考勤统计表';

-- ------------------------------------------------------------
-- 7. 请假/加班申请表（P1 功能，第 2 周开发）
-- ------------------------------------------------------------
CREATE TABLE leave_apply (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    emp_id       BIGINT       NOT NULL                COMMENT '员工ID',
    apply_type   VARCHAR(16)  NOT NULL                COMMENT 'leave请假 / overtime加班',
    reason       VARCHAR(500) DEFAULT NULL            COMMENT '申请理由',
    start_time   DATETIME     NOT NULL                COMMENT '开始时间',
    end_time     DATETIME     NOT NULL                COMMENT '结束时间',
    status       VARCHAR(16)  DEFAULT 'pending'       COMMENT 'pending待审批 / approved已批准 / rejected已驳回',
    approve_by   BIGINT       DEFAULT NULL            COMMENT '审批人ID',
    approve_time DATETIME     DEFAULT NULL            COMMENT '审批时间',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '请假加班申请表';

-- ------------------------------------------------------------
-- 8. H5 员工端账号表：员工扫码注册后登录 H5 用
-- ------------------------------------------------------------
CREATE TABLE h5_account (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(32)  NOT NULL UNIQUE          COMMENT '登录账号',
    password    VARCHAR(64)  NOT NULL                 COMMENT '密码（MD5加密存储）',
    emp_id      BIGINT       DEFAULT NULL             COMMENT '绑定的员工ID（采集后绑定）',
    status      TINYINT      NOT NULL DEFAULT 1       COMMENT '1正常 0停用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT 'H5员工端账号表';

-- ------------------------------------------------------------
-- 9. 审批记录表：请假/加班/离职等审批流水
-- ------------------------------------------------------------
CREATE TABLE approval_record (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    emp_id        BIGINT       NOT NULL              COMMENT '申请人员工ID',
    type          VARCHAR(16)  NOT NULL              COMMENT 'leave事假/sick病假/annual年假/overtime加班/compensate调休/makeup补卡/resign离职',
    start_time    DATETIME     DEFAULT NULL          COMMENT '开始时间',
    end_time      DATETIME     DEFAULT NULL          COMMENT '结束时间',
    duration_hours DECIMAL(8,2) DEFAULT NULL          COMMENT '时长（小时）',
    reason        VARCHAR(500) DEFAULT NULL          COMMENT '事由',
    attachments   VARCHAR(1000) DEFAULT NULL         COMMENT '附件URL，多个逗号分隔',
    leader_id     BIGINT       DEFAULT NULL          COMMENT '直属主管ID',
    status        VARCHAR(16)  DEFAULT 'pending'     COMMENT 'pending待审批/approved已批准/rejected已驳回/cancelled已取消',
    reject_reason VARCHAR(500) DEFAULT NULL          COMMENT '驳回意见',
    approver_id   BIGINT       DEFAULT NULL          COMMENT '审批人ID',
    approve_time  DATETIME     DEFAULT NULL          COMMENT '审批时间',
    resign_reason VARCHAR(500) DEFAULT NULL          COMMENT '离职原因（仅离职类型）',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_emp (emp_id),
    INDEX idx_leader_status (leader_id, status)
) COMMENT '审批记录表';

-- ------------------------------------------------------------
-- 初始数据：默认管理员账号 admin / 密码 123456
-- ------------------------------------------------------------
INSERT INTO sys_user (username, password, real_name) VALUES ('admin', MD5('123456'), '系统管理员');
