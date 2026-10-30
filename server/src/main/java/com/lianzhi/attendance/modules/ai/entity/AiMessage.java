package com.lianzhi.attendance.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** AI 会话消息 */
@Data
@TableName("ai_message")
public class AiMessage {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** user / assistant */
    private String role;

    /** 消息正文 */
    private String content;

    /** JSON：来源引用 [{source, snippet}]，用于"查看原文来源" */
    private String sources;

    private LocalDateTime createTime;
}
