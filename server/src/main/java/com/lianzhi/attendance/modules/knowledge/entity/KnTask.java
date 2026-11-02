package com.lianzhi.attendance.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 知识库入库任务 */
@Data
@TableName("kn_task")
public class KnTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    /** queued→parsing→chunking→embedding→done/failed */
    private String stage;
    /** 0-100 */
    private Integer progress;
    private String message;
    private Integer retryCount;
    private Boolean canceled;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
