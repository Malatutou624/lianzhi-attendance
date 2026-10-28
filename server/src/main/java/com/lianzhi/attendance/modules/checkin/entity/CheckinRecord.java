package com.lianzhi.attendance.modules.checkin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("checkin_record")
public class CheckinRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long empId;

    private Long venueId;

    /** 签到时间（上班打卡） */
    private LocalDateTime signInTime;

    /** 签退时间（下班打卡） */
    private LocalDateTime signOutTime;

    private String checkinType;

    /** 签到照片URL */
    private String photoUrl;

    /** 签退照片URL */
    private String signOutPhoto;

    private BigDecimal similarity;

    private BigDecimal liveness;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String deviceInfo;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String empName;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String venueName;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String dept;
}
