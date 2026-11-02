package com.lianzhi.attendance.modules.knowledge.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.knowledge.entity.*;
import com.lianzhi.attendance.modules.knowledge.mapper.KnDocumentMapper;
import com.lianzhi.attendance.modules.knowledge.service.KnowledgeService;
import com.lianzhi.attendance.modules.knowledge.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库管理 API（管理端）
 *
 * 路由前缀 /knowledge
 * （注意：前端 Vite dev proxy 需要把 /knowledge 代理到后端 server）
 */
@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    @Autowired KnowledgeService knowledgeService;
    @Autowired TaskService taskService;
    @Autowired KnDocumentMapper documentMapper;

    // ==================== 分类 ====================

    @GetMapping("/categories")
    public Result<List<KnCategory>> listCategories() {
        return Result.ok(knowledgeService.listCategories());
    }

    @PostMapping("/categories")
    public Result<KnCategory> saveCategory(@RequestBody KnCategory category) {
        return Result.ok(knowledgeService.saveCategory(category));
    }

    @DeleteMapping("/categories/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        knowledgeService.deleteCategory(id);
        return Result.ok();
    }

    // ==================== 文档 ====================

    /** 上传文档（MultipartFile） */
    @PostMapping("/documents/upload")
    public Result<KnDocument> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "versionNote", required = false) String versionNote,
            @RequestParam(value = "effectiveAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime effectiveAt,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "operator", required = false) String operator) {
        try {
            if (file == null || file.isEmpty()) {
                return Result.fail("请选择要上传的文件");
            }
            KnDocument doc = knowledgeService.upload(file, name, categoryId,
                    versionNote, effectiveAt, tags, operator);
            // 创建异步入库任务
            taskService.createAndRun(doc.getId());
            return Result.ok(doc);
        } catch (Exception e) {
            return Result.fail("上传失败: " + e.getMessage());
        }
    }

    /** 文档列表（分页） */
    @GetMapping("/documents")
    public Result<Page<KnDocument>> pageDocuments(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        Page<KnDocument> page = documentMapper.selectPage(new Page<>(current, size),
                Wrappers.<KnDocument>lambdaQuery()
                        .eq(KnDocument::getActive, true)
                        .eq(categoryId != null, KnDocument::getCategoryId, categoryId)
                        .eq(status != null && !status.isBlank(), KnDocument::getStatus, status)
                        .like(keyword != null && !keyword.isBlank(), KnDocument::getName, keyword)
                        .orderByDesc(KnDocument::getUploadedAt));
        return Result.ok(page);
    }

    @GetMapping("/documents/{id}")
    public Result<KnDocument> getDocument(@PathVariable Long id) {
        KnDocument doc = knowledgeService.getDoc(id);
        if (doc == null) return Result.fail("文档不存在");
        return Result.ok(doc);
    }

    @DeleteMapping("/documents/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        knowledgeService.deleteDoc(id);
        return Result.ok();
    }

    /** 重新入库（失败后重试） */
    @PostMapping("/documents/{id}/reprocess")
    public Result<KnTask> reprocess(@PathVariable Long id) {
        knowledgeService.reprocess(id);
        KnTask task = taskService.createAndRun(id);
        return Result.ok(task);
    }

    // ==================== 切片 ====================

    @GetMapping("/chunks")
    public Result<List<KnChunk>> listChunks(@RequestParam Long docId) {
        return Result.ok(knowledgeService.listChunks(docId));
    }

    @PutMapping("/chunks/{id}")
    public Result<KnChunk> updateChunk(@PathVariable Long id,
                                       @RequestBody KnChunk body) {
        return Result.ok(knowledgeService.updateChunk(id, body.getContent(), body.getTitle()));
    }

    @DeleteMapping("/chunks/{id}")
    public Result<Void> deleteChunk(@PathVariable Long id) {
        knowledgeService.deleteChunk(id);
        return Result.ok();
    }
}
