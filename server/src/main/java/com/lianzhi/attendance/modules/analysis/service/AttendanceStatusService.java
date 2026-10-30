package com.lianzhi.attendance.modules.analysis.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lianzhi.attendance.modules.approval.entity.ApprovalRecord;
import com.lianzhi.attendance.modules.approval.service.ApprovalService;
import com.lianzhi.attendance.modules.checkin.entity.CheckinRecord;
import com.lianzhi.attendance.modules.checkin.service.CheckinService;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.rule.entity.AttendRule;
import com.lianzhi.attendance.modules.rule.service.RuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统一考勤状态判定服务（系统唯一入口，杜绝多口径）
 *
 * 7 种互斥状态：
 *   normal     正常    正常上下班（签到未迟到 且 签退未早退）
 *   late       迟到    上班晚了，正常下班
 *   early      早退    正常上班，下班早了
 *   lateEarly  迟到且早退  上班晚了 + 下班早了
 *   leave      请假    当天有已批准请假单覆盖
 *   absent     旷工    整天既没签到也没签退
 *   other      其他    以上都不满足（如只签了到没签退、只签了退没签到等异常）
 *
 * 判定优先级：请假 > 旷工 > 签到+签退组合 > 其他
 */
@Service
public class AttendanceStatusService {

    public static final String NORMAL = "normal";
    public static final String LATE = "late";
    public static final String EARLY = "early";
    public static final String LATE_EARLY = "lateEarly";
    public static final String LEAVE = "leave";
    public static final String ABSENT = "absent";
    public static final String OTHER = "other";

    /** 状态 → 中文 */
    public static final Map<String, String> CN = Map.of(
            NORMAL, "正常",
            LATE, "迟到",
            EARLY, "早退",
            LATE_EARLY, "迟到且早退",
            LEAVE, "请假",
            ABSENT, "旷工",
            OTHER, "其他"
    );

    /** 请假类审批类型 */
    private static final Set<String> LEAVE_TYPES = Set.of("leave", "sick", "annual");
    /** 无规则场所的默认规则 */
    private static final LocalTime DEFAULT_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_END = LocalTime.of(18, 0);
    private static final int DEFAULT_GRACE = 10;

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private RuleService ruleService;

    // ========== 核心判定 ==========

    /**
     * 判定某员工某一天的考勤状态
     * @param emp 员工
     * @param day 自然日
     * @return 7 种互斥状态之一
     */
    public String judge(Employee emp, LocalDate day) {
        // 1. 请假优先：当天有已批准请假单覆盖
        if (hasApprovedLeave(emp.getId(), day)) return LEAVE;

        // 2. 查当天打卡记录
        CheckinRecord rec = checkinService.getOne(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(CheckinRecord::getEmpId, emp.getId())
                        .ge(CheckinRecord::getSignInTime, day.atStartOfDay())
                        .lt(CheckinRecord::getSignInTime, day.plusDays(1).atStartOfDay())
                        .last("LIMIT 1"));

        // 3. 旷工：整天没打卡
        if (rec == null) return ABSENT;

        // 4. 按签到+签退时间综合判定
        AttendRule rule = ruleService.getByVenueId(emp.getVenueId());
        return judgeByRecord(rec, rule);
    }

    /**
     * 已知打卡记录+规则时的综合判定（供 H5Controller 签退时实时更新 status）
     */
    public String judgeByRecord(CheckinRecord rec, AttendRule rule) {
        if (rec.getSignInTime() == null && rec.getSignOutTime() == null) return ABSENT;

        LocalTime workStart = rule == null ? DEFAULT_START : rule.getWorkStart();
        LocalTime workEnd = rule == null ? DEFAULT_END : rule.getWorkEnd();
        int grace = rule == null ? DEFAULT_GRACE : rule.getLateMinutes();

        boolean hasIn = rec.getSignInTime() != null;
        boolean hasOut = rec.getSignOutTime() != null;

        // 只签了到没签退 / 只签了退没签到 → 其他
        if (!hasIn || !hasOut) return OTHER;

        boolean isLate = rec.getSignInTime().toLocalTime().isAfter(workStart.plusMinutes(grace));
        boolean isEarly = rec.getSignOutTime().toLocalTime().isBefore(workEnd);

        if (isLate && isEarly) return LATE_EARLY;
        if (isLate) return LATE;
        if (isEarly) return EARLY;
        return NORMAL;
    }

    // ========== 批量统计 ==========

    /**
     * 统计一天内各状态的人数分布（返回按状态分组的计数）
     */
    public Map<String, Long> countByDay(List<Employee> emps, LocalDate day) {
        Map<String, Long> result = new HashMap<>();
        // 预填 7 种状态，保证前端饼图不会缺项
        for (String s : List.of(NORMAL, LATE, EARLY, LATE_EARLY, LEAVE, ABSENT, OTHER)) {
            result.put(s, 0L);
        }

        // 批量查当天打卡记录
        List<Long> empIds = emps.stream().map(Employee::getId).toList();
        Map<Long, CheckinRecord> recMap = checkinService.list(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .in(!empIds.isEmpty(), CheckinRecord::getEmpId, empIds)
                        .ge(CheckinRecord::getSignInTime, day.atStartOfDay())
                        .lt(CheckinRecord::getSignInTime, day.plusDays(1).atStartOfDay())
        ).stream().collect(Collectors.toMap(CheckinRecord::getEmpId, r -> r, (a, b) -> a));

        // 批量查当天请假单（已批准）
        Map<Long, Boolean> leaveMap = new HashMap<>();
        if (!empIds.isEmpty()) {
            List<ApprovalRecord> leaves = approvalService.list(
                    Wrappers.<ApprovalRecord>lambdaQuery()
                            .in(ApprovalRecord::getEmpId, empIds)
                            .eq(ApprovalRecord::getStatus, "approved")
                            .in(ApprovalRecord::getType, LEAVE_TYPES)
            );
            for (ApprovalRecord a : leaves) {
                LocalDate start = a.getStartTime().toLocalDate();
                LocalDate end = a.getEndTime().toLocalDate();
                if (!day.isBefore(start) && !day.isAfter(end)) {
                    leaveMap.put(a.getEmpId(), true);
                }
            }
        }

        for (Employee emp : emps) {
            String status;
            if (leaveMap.containsKey(emp.getId())) {
                status = LEAVE;
            } else if (!recMap.containsKey(emp.getId())) {
                status = ABSENT;
            } else {
                AttendRule rule = ruleService.getByVenueId(emp.getVenueId());
                status = judgeByRecord(recMap.get(emp.getId()), rule);
            }
            result.merge(status, 1L, Long::sum);
        }
        return result;
    }

    /**
     * 列出一天内指定状态的员工明细
     */
    public List<Map<String, Object>> peopleByStatus(List<Employee> emps, LocalDate day, String targetStatus) {
        Map<String, Long> counts = countByDay(emps, day); // 复用批量查询结果高效实现
        // 重新算一遍（countByDay 已高效批量查了，这里直接遍历）
        List<Map<String, Object>> result = new ArrayList<>();
        Map<Long, CheckinRecord> recMap = new HashMap<>();
        Map<Long, Boolean> leaveMap = new HashMap<>();

        List<Long> empIds = emps.stream().map(Employee::getId).toList();
        if (!empIds.isEmpty()) {
            recMap = checkinService.list(
                    Wrappers.<CheckinRecord>lambdaQuery()
                            .in(CheckinRecord::getEmpId, empIds)
                            .ge(CheckinRecord::getSignInTime, day.atStartOfDay())
                            .lt(CheckinRecord::getSignInTime, day.plusDays(1).atStartOfDay())
            ).stream().collect(Collectors.toMap(CheckinRecord::getEmpId, r -> r, (a, b) -> a));

            List<ApprovalRecord> leaves = approvalService.list(
                    Wrappers.<ApprovalRecord>lambdaQuery()
                            .in(ApprovalRecord::getEmpId, empIds)
                            .eq(ApprovalRecord::getStatus, "approved")
                            .in(ApprovalRecord::getType, LEAVE_TYPES)
            );
            for (ApprovalRecord a : leaves) {
                LocalDate start = a.getStartTime().toLocalDate();
                LocalDate end = a.getEndTime().toLocalDate();
                if (!day.isBefore(start) && !day.isAfter(end)) {
                    leaveMap.put(a.getEmpId(), true);
                }
            }
        }

        Map<Long, String> venueMap = new HashMap<>();
        for (Employee e : emps) {
            AttendRule rule = ruleService.getByVenueId(e.getVenueId());
            String s;
            if (leaveMap.containsKey(e.getId())) s = LEAVE;
            else if (!recMap.containsKey(e.getId())) s = ABSENT;
            else s = judgeByRecord(recMap.get(e.getId()), rule);

            if (targetStatus != null && !targetStatus.equals(s)) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("id", e.getId());
            item.put("empNo", e.getEmpNo());
            item.put("name", e.getName());
            item.put("dept", e.getDept());
            item.put("position", e.getPosition());
            item.put("venueId", e.getVenueId());
            item.put("status", s);
            item.put("statusText", CN.getOrDefault(s, s));
            CheckinRecord r = recMap.get(e.getId());
            if (r != null) {
                item.put("checkinTime", r.getSignInTime().toString().replace("T", " ").substring(0, 19));
            }
            result.add(item);
        }
        return result;
    }

    // ========== 辅助 ==========

    private boolean hasApprovedLeave(Long empId, LocalDate day) {
        List<ApprovalRecord> leaves = approvalService.list(
                Wrappers.<ApprovalRecord>lambdaQuery()
                        .eq(ApprovalRecord::getEmpId, empId)
                        .eq(ApprovalRecord::getStatus, "approved")
                        .in(ApprovalRecord::getType, LEAVE_TYPES));
        for (ApprovalRecord a : leaves) {
            LocalDate start = a.getStartTime().toLocalDate();
            LocalDate end = a.getEndTime().toLocalDate();
            if (!day.isBefore(start) && !day.isAfter(end)) return true;
        }
        return false;
    }

    /** 取某场所规则（带缓存语义，RuleService 对未配置场所已内置默认） */
    public AttendRule ruleFor(Long venueId) {
        if (venueId == null) {
            AttendRule r = new AttendRule();
            r.setWorkStart(DEFAULT_START);
            r.setWorkEnd(DEFAULT_END);
            r.setLateMinutes(DEFAULT_GRACE);
            return r;
        }
        return ruleService.getByVenueId(venueId);
    }
}
