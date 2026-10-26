package com.lianzhi.attendance.modules.rule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalTime;

/**
 * 考勤规则，对应 attend_rule 表
 * 每个场所一条规则：几点上班、几点下班、晚到多少分钟算迟到
 */
@Data
@TableName("attend_rule")
public class AttendRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 场所ID（每个场所一条规则） */
    private Long venueId;

    /** 上班时间，如 09:00 */
    private LocalTime workStart;

    /** 下班时间，如 18:00 */
    private LocalTime workEnd;

    /** 宽限分钟数：超过上班时间这么多分钟才算迟到 */
    private Integer lateMinutes;

    // ---- 非数据库字段：给前端表格显示场所名 ----
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String venueName;
}
