package com.lianzhi.attendance.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 会话 */
@Data
@TableName("ai_session")
public class AiSession {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID（管理端=sys_user.id, H5=h5_account.id） */
    private Long userId;

    /** 用户类型：admin / h5 */
    private String userType;

    /** 会话标题（首次提问后自动取首问前 20 字） */
    private String title;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
