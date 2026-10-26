package com.lianzhi.attendance.modules.checkin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 打卡记录，对应 checkin_record 表
 */
@Data
@TableName("checkin_record")
public class CheckinRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long empId;

    private Long venueId;

    /** 打卡时间 */
    private LocalDateTime checkinTime;

    /** face人脸识别 / photo拍照 */
    private String checkinType;

    /** 打卡现场照片URL */
    private String photoUrl;

    /** 人脸比对相似度 0-100 */
    private BigDecimal similarity;

    /** 活体检测分数 0-1 */
    private BigDecimal liveness;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String deviceInfo;

    /** normal正常 / late迟到 / early早退 */
    private String status;

    private LocalDateTime createTime;

    // ---- 非数据库字段：给前端展示用 ----
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String empName;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String venueName;
}
