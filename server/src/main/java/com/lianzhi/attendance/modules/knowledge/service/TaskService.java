package com.lianzhi.attendance.modules.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianzhi.attendance.modules.knowledge.entity.KnTask;
import com.lianzhi.attendance.modules.knowledge.mapper.KnTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库异步入库任务管理
 *
 * 为什么不用 @Async：课程项目 Spring Boot 2.7 没开 Async 配置，
 * 简单 new Thread().start() 够用（数据量小，答辩也能讲）。
 */
@Slf4j
@Service
public class TaskService {

    @Autowired KnTaskMapper taskMapper;
    @Autowired KnowledgeService knowledgeService;

    /** 创建并立即执行异步入库任务 */
    public KnTask createAndRun(Long docId) {
        KnTask task = new KnTask();
        task.setDocId(docId);
        task.setStage("queued");
        task.setProgress(0);
        task.setMessage("任务已排队");
        task.setRetryCount(0);
        task.setCanceled(false);
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        // 异步执行
        new Thread(() -> runInternal(task.getId()), "kn-ingest-" + docId).start();
        return task;
    }

    /** 重试失败/取消的任务 */
    public KnTask retry(Long taskId) {
        KnTask task = taskMapper.selectById(taskId);
        if (task == null) return null;
        task.setStage("queued");
        task.setProgress(0);
        task.setMessage("重试中...");
        task.setCanceled(false);
        taskMapper.updateById(task);
        new Thread(() -> runInternal(taskId), "kn-retry-" + taskId).start();
        return task;
    }

    /** 取消任务 */
    public void cancel(Long taskId) {
        KnTask task = taskMapper.selectById(taskId);
        if (task == null) return;
        task.setCanceled(true);
        task.setMessage("已取消");
        taskMapper.updateById(task);
    }

    public List<KnTask> listTasks() {
        return taskMapper.selectList(new LambdaQueryWrapper<KnTask>()
                .orderByDesc(KnTask::getCreatedAt)
                .last("LIMIT 200"));
    }

    public KnTask getTask(Long id) { return taskMapper.selectById(id); }

    private void runInternal(Long taskId) {
        KnTask task = taskMapper.selectById(taskId);
        if (task == null) return;

        task.setStage("parsing");
        task.setProgress(10);
        task.setMessage("开始解析...");
        task.setStartedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        try {
            // 同步调用主服务的 runIngest（内部已包含 解析→切片→向量化 全流程）
            // runIngest 内部会改 KnDocument.status，但任务的 stage/progress 我们自己维护
            knowledgeService.runIngest(task.getDocId());

            task.setStage("done");
            task.setProgress(100);
            task.setMessage("入库完成");
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);

            log.info("[TaskService] 任务完成: taskId={} docId={}", taskId, task.getDocId());
        } catch (Exception e) {
            log.error("[TaskService] 任务失败: taskId={} docId={}", taskId, task.getDocId(), e);
            KnTask failed = taskMapper.selectById(taskId);
            if (failed != null) {
                failed.setStage("failed");
                failed.setMessage("失败: " + e.getMessage());
                failed.setFinishedAt(LocalDateTime.now());
                failed.setRetryCount((failed.getRetryCount() == null ? 0 : failed.getRetryCount()) + 1);
                taskMapper.updateById(failed);
            }
        }
    }
}
