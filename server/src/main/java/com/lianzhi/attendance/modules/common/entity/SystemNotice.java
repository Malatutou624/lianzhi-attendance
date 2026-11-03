package com.lianzhi.attendance.modules.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 系统通知 / 站内信（补卡提醒、月报通知等） */
@Data
@TableName("system_notice")
public class SystemNotice {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 目标用户 ID */
    private Long userId;
    /** admin / h5 / all */
    private String userType;
    /** MAKEUP_REMIND / MONTHLY_REPORT / SYSTEM */
    private String type;
    private String title;
    private String content;
    /** 关联业务 ID */
    private Long relatedId;
    private Boolean isRead;
    private LocalDateTime createTime;
}
