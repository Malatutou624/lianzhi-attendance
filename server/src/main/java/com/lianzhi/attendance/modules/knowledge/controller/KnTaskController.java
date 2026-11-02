package com.lianzhi.attendance.modules.knowledge.controller;

import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.knowledge.entity.KnTask;
import com.lianzhi.attendance.modules.knowledge.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 入库任务管理 API
 */
@RestController
@RequestMapping("/knowledge/tasks")
public class KnTaskController {

    @Autowired TaskService taskService;

    @GetMapping
    public Result<List<KnTask>> list() {
        return Result.ok(taskService.listTasks());
    }

    @GetMapping("/{id}")
    public Result<KnTask> get(@PathVariable Long id) {
        return Result.ok(taskService.getTask(id));
    }

    @PostMapping("/{id}/retry")
    public Result<KnTask> retry(@PathVariable Long id) {
        return Result.ok(taskService.retry(id));
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        taskService.cancel(id);
        return Result.ok();
    }
}
