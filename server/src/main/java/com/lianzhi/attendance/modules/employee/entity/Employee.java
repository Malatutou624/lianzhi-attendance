package com.lianzhi.attendance.modules.employee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工，对应 employee 表
 */
@Data
@TableName("employee")
public class Employee {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工号，唯一 */
    private String empNo;

    private String name;

    /** 1男 2女 */
    private Integer gender;

    private String phone;

    /** 身份证号（人证核验用） */
    private String idCard;

    private String dept;

    private String position;

    /** 所属办公场所ID */
    private Long venueId;

    /** 人脸照片URL */
    private String facePhoto;

    /** 人脸特征值（虹软SDK提取，Base64字符串，第2周接入） */
    private String faceFeature;

    /** 1在职 2病假 3事假 0离职 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
