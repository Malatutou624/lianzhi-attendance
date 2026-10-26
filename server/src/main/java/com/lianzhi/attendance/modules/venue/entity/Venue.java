package com.lianzhi.attendance.modules.venue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 办公场所，对应 venue 表
 */
@Data
@TableName("venue")
public class Venue {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 场所名称，如：北京总部 */
    private String name;

    private String companyName;

    /** 上级场所ID，0=总部（支持公司-子公司层级） */
    private Long parentId;

    private String address;

    /** 经度（地图坐标采集） */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 允许打卡范围（米） */
    private Integer radius;

    /** 场所码参数 */
    private String qrcodeParam;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
