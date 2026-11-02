package com.lianzhi.attendance.modules.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 知识库文档 */
@Data
@TableName("kn_document")
public class KnDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String name;
    private String fileName;
    private String filePath;
    private Long fileSize;
    /** pdf/docx/xlsx/pptx/txt/image/audio/video */
    private String format;
    private Integer version;
    private String versionNote;
    private LocalDateTime effectiveAt;
    private String tags;
    /** pending→parsing→chunking→embedding→ready/failed/deleted */
    private String status;
    private String parseError;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
    private LocalDateTime updatedAt;
    private Boolean active;
}
