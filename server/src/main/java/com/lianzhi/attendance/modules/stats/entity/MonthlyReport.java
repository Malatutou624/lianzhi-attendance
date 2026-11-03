package com.lianzhi.attendance.modules.stats.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 考勤月报 */
@Data
@TableName("monthly_report")
public class MonthlyReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 月份 YYYY-MM */
    private String reportMonth;
    private Long empId;
    private String empName;
    private String dept;
    private Long venueId;
    private String venueName;
    private Integer workDays;
    private Integer lateCount;
    private Integer earlyCount;
    private Integer absentCount;
    private Integer leaveCount;
    private BigDecimal overtimeHours;
    private BigDecimal totalHours;
    private LocalDateTime createTime;
}
