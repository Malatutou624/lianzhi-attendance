package com.lianzhi.attendance.modules.approval.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.approval.entity.ApprovalRecord;
import com.lianzhi.attendance.modules.approval.service.ApprovalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 审批接口（供移动端 H5 和 PC 管理端共用）
 * H5 员工端直接传 empId/leaderId；PC 管理端通过 token 获取当前登录人
 */
@RestController
@RequestMapping("/approval")
public class ApprovalController {

    @Autowired
    private ApprovalService approvalService;

    /** 提交申请 */
    @PostMapping("/submit")
    public Result<ApprovalRecord> submit(@RequestBody ApprovalRecord record, @RequestParam Long empId) {
        return Result.ok(approvalService.submit(record, empId));
    }

    /** 撤回申请 */
    @PostMapping("/cancel/{id:\\d+}")
    public Result<Void> cancel(@PathVariable Long id, @RequestParam Long empId) {
        approvalService.cancel(id, empId);
        return Result.ok();
    }

    /** 我的申请列表 */
    @GetMapping("/my")
    public Result<Page<ApprovalRecord>> my(@RequestParam Long empId,
                                            @RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) String status) {
        return Result.ok(approvalService.myPage(empId, current, size, type, status));
    }

    /** 待我审批列表（主管） */
    @GetMapping("/todo")
    public Result<Page<ApprovalRecord>> todo(@RequestParam Long leaderId,
                                              @RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String type) {
        return Result.ok(approvalService.todoPage(leaderId, current, size, type));
    }

    /** 我已处理列表（主管/HR） */
    @GetMapping("/handled")
    public Result<Page<ApprovalRecord>> handled(@RequestParam Long approverId,
                                                  @RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "10") long size,
                                                  @RequestParam(required = false) String type) {
        return Result.ok(approvalService.handledPage(approverId, current, size, type));
    }

    /** 管理端：全部单据列表（支持 type/status/empName 过滤） */
    @GetMapping("/all")
    public Result<Page<ApprovalRecord>> all(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String type,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String empName) {
        return Result.ok(approvalService.allPage(current, size, type, status, empName));
    }

    /** 审批操作（同意/驳回） */
    @PostMapping("/decide")
    public Result<Void> decide(@RequestParam Long id,
                                @RequestParam Long approverId,
                                @RequestParam boolean pass,
                                @RequestParam(required = false) String rejectReason) {
        approvalService.approve(id, approverId, pass, rejectReason);
        return Result.ok();
    }

    /** 申请详情（id 必须是数字，避免 /all 等固定路径被误匹配） */
    @GetMapping("/{id:\\d+}")
    public Result<ApprovalRecord> detail(@PathVariable Long id) {
        ApprovalRecord r = approvalService.getById(id);
        if (r != null) {
            List<ApprovalRecord> list = new ArrayList<>();
            list.add(r);
            approvalService.fillNames(list);
        }
        return Result.ok(r);
    }
}
