package com.lianzhi.attendance.modules.h5.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.approval.entity.ApprovalRecord;
import com.lianzhi.attendance.modules.approval.service.ApprovalService;
import com.lianzhi.attendance.modules.auth.entity.SysUser;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.service.EmployeeService;
import com.lianzhi.attendance.modules.h5.entity.H5Account;
import com.lianzhi.attendance.modules.h5.mapper.H5AccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * H5 员工端审批接口（供移动端发起申请/查看申请用）
 * 所有接口都通过 h5AccountId 自动解析绑定的员工 ID，前端不需要自己传 empId
 */
@RestController
@RequestMapping("/h5/approval")
public class H5ApprovalController {

    @Autowired private ApprovalService approvalService;
    @Autowired private H5AccountMapper accountMapper;
    @Autowired private EmployeeService employeeService;

    /** 内部：从 request 取当前登录 H5 账号，再解析绑定的员工；未绑定抛异常 */
    private Employee resolveBoundEmployee(HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("h5AccountId");
        H5Account acc = accountMapper.selectById(accountId);
        if (acc == null || acc.getEmpId() == null) {
            throw new RuntimeException("请先完成员工信息采集");
        }
        Employee emp = employeeService.getById(acc.getEmpId());
        if (emp == null) throw new RuntimeException("绑定的员工信息不存在");
        return emp;
    }

    /** 获取当前绑定员工的审批人信息（前端申请页显示用） */
    @GetMapping("/approver")
    public Result<Map<String, Object>> approver(HttpServletRequest request) {
        Employee emp = resolveBoundEmployee(request);
        return Result.ok(approvalService.getApproverInfo(emp.getId()));
    }

    /** 提交申请（不需要传 empId，从 h5Account 自动取） */
    @PostMapping("/submit")
    public Result<ApprovalRecord> submit(@RequestBody ApprovalRecord record, HttpServletRequest request) {
        Employee emp = resolveBoundEmployee(request);
        return Result.ok(approvalService.submit(record, emp.getId()));
    }

    /** 撤回申请 */
    @PostMapping("/cancel/{id}")
    public Result<Void> cancel(@PathVariable Long id, HttpServletRequest request) {
        Employee emp = resolveBoundEmployee(request);
        approvalService.cancel(id, emp.getId());
        return Result.ok();
    }

    /** 我的申请列表 */
    @GetMapping("/my")
    public Result<Page<ApprovalRecord>> my(HttpServletRequest request,
                                            @RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) String status) {
        Employee emp = resolveBoundEmployee(request);
        return Result.ok(approvalService.myPage(emp.getId(), current, size, type, status));
    }

    /** 申请详情 */
    @GetMapping("/{id}")
    public Result<ApprovalRecord> detail(@PathVariable Long id) {
        return Result.ok(approvalService.getById(id));
    }

    /** 待我审批列表（兼容：如果 H5 员工是部门主管也能看到；同时也给默认管理员用） */
    @GetMapping("/todo")
    public Result<Page<ApprovalRecord>> todo(HttpServletRequest request,
                                              @RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String type) {
        Employee emp = resolveBoundEmployee(request);
        return Result.ok(approvalService.todoPage(emp.getId(), current, size, type));
    }

    /** 我已处理列表 */
    @GetMapping("/handled")
    public Result<Page<ApprovalRecord>> handled(HttpServletRequest request,
                                                  @RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "10") long size,
                                                  @RequestParam(required = false) String type) {
        Employee emp = resolveBoundEmployee(request);
        return Result.ok(approvalService.handledPage(emp.getId(), current, size, type));
    }

    /** 管理员待审批（供 PC 管理端管理员处理申请：用 sys_user.id 查 leaderId=admin.id 的） */
    @GetMapping("/admin-todo")
    public Result<Page<ApprovalRecord>> adminTodo(@RequestParam(defaultValue = "1") long current,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) String type) {
        SysUser admin = approvalService.getDefaultAdmin();
        if (admin == null) return Result.ok(new Page<>());
        return Result.ok(approvalService.todoPage(admin.getId(), current, size, type));
    }

    /** 管理员审批操作 */
    @PostMapping("/decide")
    public Result<Void> decide(@RequestParam Long id,
                                @RequestParam boolean pass,
                                @RequestParam(required = false) String rejectReason,
                                HttpServletRequest request) {
        // H5 端审批：先用 sys_user 默认管理员身份
        SysUser admin = approvalService.getDefaultAdmin();
        if (admin == null) return Result.fail("未配置审批人");
        approvalService.approve(id, admin.getId(), pass, rejectReason);
        return Result.ok();
    }
}
