package com.lianzhi.attendance.modules.approval.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lianzhi.attendance.modules.approval.entity.ApprovalRecord;
import com.lianzhi.attendance.modules.approval.mapper.ApprovalRecordMapper;
import com.lianzhi.attendance.modules.auth.entity.SysUser;
import com.lianzhi.attendance.modules.auth.mapper.SysUserMapper;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ApprovalService extends ServiceImpl<ApprovalRecordMapper, ApprovalRecord> {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private SysUserMapper sysUserMapper;

    private static final Map<String, String> TYPE_TEXT = Map.of(
            "leave", "事假", "sick", "病假", "annual", "年假",
            "overtime", "加班", "compensate", "调休",
            "makeup", "补卡", "resign", "离职"
    );
    private static final Map<String, String> STATUS_TEXT = Map.of(
            "pending", "待审批", "approved", "已通过",
            "rejected", "已驳回", "cancelled", "已撤回"
    );

    /** 从 sys_user 表获取默认管理员（role=admin, status=1, 按 id 最小优先） */
    public SysUser getDefaultAdmin() {
        return sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRole, "admin")
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId)
                .last("limit 1"));
    }

    /** 填充申请人、主管姓名等展示字段（leaderId/approverId 同时查 employee 和 sys_user） */
    public void fillNames(List<ApprovalRecord> list) {
        if (list.isEmpty()) return;

        // 先把所有出现的 ID 收集起来
        List<Long> empIds = list.stream().map(ApprovalRecord::getEmpId).distinct().collect(Collectors.toList());
        List<Long> leaderIds = list.stream().map(ApprovalRecord::getLeaderId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> approverIds = list.stream().map(ApprovalRecord::getApproverId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());

        // 查 employee 表
        Map<Long, Employee> empMap = employeeService.listByIds(empIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        // leaderId/approverId 先尝试 employee，查不到再尝试 sys_user
        List<Long> needCheckSysUser = new ArrayList<>();
        needCheckSysUser.addAll(leaderIds);
        needCheckSysUser.addAll(approverIds);
        Map<Long, SysUser> sysUserMap = sysUserMapper.selectBatchIds(needCheckSysUser).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));

        for (ApprovalRecord r : list) {
            Employee emp = empMap.get(r.getEmpId());
            if (emp != null) {
                r.setEmpName(emp.getName());
                r.setEmpNo(emp.getEmpNo());
                r.setEmpDept(emp.getDept());
                r.setEmpPhone(emp.getPhone());
            }
            // leader 姓名：优先 employee 表，找不到就用 sys_user.realName
            if (r.getLeaderId() != null) {
                Employee leaderEmp = employeeService.getById(r.getLeaderId());
                if (leaderEmp != null) {
                    r.setLeaderName(leaderEmp.getName());
                } else {
                    SysUser admin = sysUserMap.get(r.getLeaderId());
                    if (admin != null) r.setLeaderName(admin.getRealName() != null ? admin.getRealName() : admin.getUsername());
                }
            }
            // approver 姓名同理
            if (r.getApproverId() != null) {
                Employee approverEmp = employeeService.getById(r.getApproverId());
                if (approverEmp != null) {
                    r.setApproverName(approverEmp.getName());
                } else {
                    SysUser admin = sysUserMap.get(r.getApproverId());
                    if (admin != null) r.setApproverName(admin.getRealName() != null ? admin.getRealName() : admin.getUsername());
                }
            }
            r.setTypeText(TYPE_TEXT.getOrDefault(r.getType(), r.getType()));
            r.setStatusText(STATUS_TEXT.getOrDefault(r.getStatus(), r.getStatus()));
        }
    }

    public void fillNames(Page<ApprovalRecord> page) { fillNames(page.getRecords()); }

    /** 自动计算时长（小时） */
    public void calcDuration(ApprovalRecord record) {
        if ("resign".equals(record.getType())) return;
        if (record.getStartTime() != null && record.getEndTime() != null
                && record.getDurationHours() == null) {
            long ms = Duration.between(record.getStartTime(), record.getEndTime()).toMillis();
            record.setDurationHours(BigDecimal.valueOf(ms / (1000.0 * 60 * 60)).setScale(2, RoundingMode.HALF_UP));
        }
    }

    /** 获取员工的审批人信息（返回 Map: leaderId + leaderName，前端申请页显示用） */
    public Map<String, Object> getApproverInfo(Long empId) {
        Employee emp = employeeService.getById(empId);
        if (emp == null) return null;
        Long leaderId = emp.getLeaderId();
        String leaderName = null;
        if (leaderId != null) {
            Employee leaderEmp = employeeService.getById(leaderId);
            if (leaderEmp != null) {
                leaderName = leaderEmp.getName();
            } else {
                SysUser admin = sysUserMapper.selectById(leaderId);
                if (admin != null) leaderName = admin.getRealName() != null ? admin.getRealName() : admin.getUsername();
            }
        } else {
            // 无直属主管 → fallback 到系统管理员
            SysUser admin = getDefaultAdmin();
            if (admin != null) {
                leaderId = admin.getId();
                leaderName = admin.getRealName() != null ? admin.getRealName() : admin.getUsername();
            }
        }
        Map<String, Object> info = new java.util.HashMap<>();
        info.put("leaderId", leaderId);
        info.put("leaderName", leaderName);
        info.put("isAdminFallback", leaderId != null && employeeService.getById(leaderId) == null);
        return info;
    }

    /** 提交申请：自动带出直属主管，没有则 fallback 到系统管理员 */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalRecord submit(ApprovalRecord record, Long empId) {
        Employee emp = employeeService.getById(empId);
        if (emp == null) throw new RuntimeException("员工不存在");
        record.setEmpId(empId);
        // 无直属主管 → fallback 到系统管理员（sys_user.id）
        Long leaderId = emp.getLeaderId();
        if (leaderId == null) {
            SysUser admin = getDefaultAdmin();
            if (admin != null) leaderId = admin.getId();
        }
        record.setLeaderId(leaderId);
        record.setStatus("pending");
        record.setCreateTime(LocalDateTime.now());
        if (!"resign".equals(record.getType())) calcDuration(record);
        save(record);
        return record;
    }

    /** 审批（同意/驳回） */
    @Transactional(rollbackFor = Exception.class)
    public boolean approve(Long id, Long approverId, boolean pass, String rejectReason) {
        ApprovalRecord r = getById(id);
        if (r == null) throw new RuntimeException("申请不存在");
        if (!"pending".equals(r.getStatus())) throw new RuntimeException("该申请已处理");
        r.setApproverId(approverId);
        r.setApproveTime(LocalDateTime.now());
        if (pass) {
            r.setStatus("approved");
            r.setRejectReason(null);
        } else {
            r.setStatus("rejected");
            r.setRejectReason(rejectReason);
        }
        return updateById(r);
    }

    /** 撤回申请 */
    public boolean cancel(Long id, Long empId) {
        ApprovalRecord r = getById(id);
        if (r == null) throw new RuntimeException("申请不存在");
        if (!r.getEmpId().equals(empId)) throw new RuntimeException("无权操作");
        if (!"pending".equals(r.getStatus())) throw new RuntimeException("仅待审批的申请可撤回");
        r.setStatus("cancelled");
        return updateById(r);
    }

    /** 我的申请列表 */
    public Page<ApprovalRecord> myPage(Long empId, long current, long size, String type, String status) {
        Page<ApprovalRecord> page = page(new Page<>(current, size),
                Wrappers.<ApprovalRecord>lambdaQuery()
                        .eq(ApprovalRecord::getEmpId, empId)
                        .eq(type != null && !type.isEmpty(), ApprovalRecord::getType, type)
                        .eq(status != null && !status.isEmpty(), ApprovalRecord::getStatus, status)
                        .orderByDesc(ApprovalRecord::getCreateTime));
        fillNames(page);
        return page;
    }

    /** 待我审批列表（leaderId 可以是 employee.id 或 sys_user.id） */
    public Page<ApprovalRecord> todoPage(Long leaderId, long current, long size, String type) {
        Page<ApprovalRecord> page = page(new Page<>(current, size),
                Wrappers.<ApprovalRecord>lambdaQuery()
                        .eq(ApprovalRecord::getLeaderId, leaderId)
                        .eq(ApprovalRecord::getStatus, "pending")
                        .eq(type != null && !type.isEmpty(), ApprovalRecord::getType, type)
                        .orderByAsc(ApprovalRecord::getCreateTime));
        fillNames(page);
        return page;
    }

    /** 管理端：全部单据列表（支持按 type/status/empName 过滤） */
    public Page<ApprovalRecord> allPage(long current, long size, String type, String status, String empName) {
        // empName 过滤：先查 employee 表得到匹配的 empIds
        List<Long> empIds = null;
        if (empName != null && !empName.trim().isEmpty()) {
            empIds = employeeService.list(Wrappers.<Employee>lambdaQuery()
                            .like(Employee::getName, empName.trim()))
                    .stream().map(Employee::getId).collect(Collectors.toList());
            if (empIds.isEmpty()) {
                // 没有匹配员工 → 直接返回空页
                Page<ApprovalRecord> empty = new Page<>(current, size);
                empty.setRecords(new ArrayList<>());
                empty.setTotal(0);
                return empty;
            }
        }

        Page<ApprovalRecord> page = page(new Page<>(current, size),
                Wrappers.<ApprovalRecord>lambdaQuery()
                        .eq(type != null && !type.isEmpty(), ApprovalRecord::getType, type)
                        .eq(status != null && !status.isEmpty(), ApprovalRecord::getStatus, status)
                        .in(empIds != null && !empIds.isEmpty(), ApprovalRecord::getEmpId, empIds)
                        .orderByDesc(ApprovalRecord::getCreateTime));
        fillNames(page);
        return page;
    }

    /** 我已处理列表 */
    public Page<ApprovalRecord> handledPage(Long approverId, long current, long size, String type) {
        Page<ApprovalRecord> page = page(new Page<>(current, size),
                Wrappers.<ApprovalRecord>lambdaQuery()
                        .eq(ApprovalRecord::getApproverId, approverId)
                        .ne(ApprovalRecord::getStatus, "pending")
                        .eq(type != null && !type.isEmpty(), ApprovalRecord::getType, type)
                        .orderByDesc(ApprovalRecord::getApproveTime));
        fillNames(page);
        return page;
    }
}
