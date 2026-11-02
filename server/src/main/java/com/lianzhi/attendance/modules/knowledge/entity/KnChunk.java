package com.lianzhi.attendance.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 知识切片 */
@Data
@TableName("kn_chunk")
public class KnChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private Integer indexNo;
    private String title;
    private String content;
    /** 来源定位：页码/章节名 */
    private String sourceRef;
    /** 向量 JSON 数组字符串 */
    private String embedding;
    /** tfidf / dashscope */
    private String alg;
    private LocalDateTime createdAt;
}
