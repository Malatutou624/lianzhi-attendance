package com.lianzhi.attendance.modules.stats.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日考勤统计表，对应 daily_stats 表
 * 每天 00:30 由定时任务自动生成前一日数据（DailyStatsTask）
 */
@Data
@TableName("daily_stats")
public class DailyStats {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 统计日期 */
    private LocalDate statDate;

    /** 场所ID */
    private Long venueId;

    /** 应到人数（该场所在职员工数） */
    private Integer empTotal;

    /** 实际打卡人数 */
    private Integer checkinTotal;

    /** 迟到人数 */
    private Integer lateTotal;

    /** 缺勤人数 */
    private Integer absentTotal;

    private LocalDateTime createTime;
}
