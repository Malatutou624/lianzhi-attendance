package com.lianzhi.attendance.modules.h5.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** H5 员工端账号表 */
@Data
@TableName("h5_account")
public class H5Account {
    private Long id;
    private String username;
    private String password;
    /** 微信 openid（小程序登录自动绑定） */
    private String openid;
    /** 绑定的员工ID（采集后绑定，唯一） */
    private Long empId;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
