package com.lianzhi.attendance.modules.analysis.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lianzhi.attendance.modules.analysis.util.ExcelUtil;
import com.lianzhi.attendance.modules.approval.entity.ApprovalRecord;
import com.lianzhi.attendance.modules.approval.service.ApprovalService;
import com.lianzhi.attendance.modules.checkin.entity.CheckinRecord;
import com.lianzhi.attendance.modules.checkin.service.CheckinService;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.service.EmployeeService;
import com.lianzhi.attendance.modules.rule.entity.AttendRule;
import com.lianzhi.attendance.modules.rule.service.RuleService;
import com.lianzhi.attendance.modules.venue.service.VenueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 子功能2：员工考勤工时分析
 *
 * 统计口径（与审批中心联动）：
 * - 出勤天数：范围内有签到记录的自然日数
 * - 迟到/早退：按打卡时间对照该场所考勤规则独立判定
 *     迟到 = 签到时间晚于 上班时间+宽限分钟
 *     早退 = 签退时间早于 下班时间
 *   注意：打卡时 status 字段只存最后一次打卡的结论（签退会覆盖签到状态），
 *   所以这里不用 status 字段，而是逐次打卡独立判定，保证四类（正常/迟到/早退/缺卡）互不排斥：
 *   例如"早上正常打卡但提前走了"会同时计入 正常(签到正常) 和 早退
 * - 缺卡次数：缺签到的次数 + 缺签退的次数
 * - 正常工时：签到到签退的时长累加
 * - 加班/请假/调休时长：只统计审批状态=已通过(approved)且开始时间落在统计范围内的单据
 *   待审批/已驳回/已撤销的单据一律不计入，不影响统计结果
 * - 数据实时从原始表计算，打卡或审批数据变更后报表自动更新
 */
@Service
public class WorktimeService {

    /** 审批类型 → 中文 */
    private static final Map<String, String> TYPE_TEXT = Map.of(
            "leave", "事假", "sick", "病假", "annual", "年假",
            "overtime", "加班", "compensate", "调休", "makeup", "补卡", "resign", "离职");
    /** 请假类类型（计入请假时长） */
    private static final Set<String> LEAVE_TYPES = Set.of("leave", "sick", "annual");
    /** 无规则场所的默认规则：9点上班 18点下班 宽限10分钟 */
    private static final LocalTime DEFAULT_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_END = LocalTime.of(18, 0);
    private static final int DEFAULT_GRACE = 10;

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private VenueService venueService;

    @Autowired
    private RuleService ruleService;

    @Autowired
    private AttendanceStatusService statusService;

    /**
     * 考勤工时汇总
     * @param dept    部门，空=全部
     * @param venueId 场所，空=全部
     * @param start   开始日期（含）
     * @param end     结束日期（含）
     * @return emps: 员工明细列表；depts: 部门汇总（柱状图）；anomaly: 考勤异常占比（饼图，四类独立计数）
     */
    public Map<String, Object> summary(String dept, Long venueId, LocalDate start, LocalDate end) {
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();

        // 员工范围（按部门+场所过滤，保留无记录员工，汇总表才完整）
        List<Employee> emps = new ArrayList<>();
        for (Employee e : employeeService.list()) {
            boolean deptMatch = dept == null || dept.isEmpty() || dept.equals(e.getDept());
            boolean venueMatch = venueId == null || venueId.equals(e.getVenueId());
            if (deptMatch && venueMatch) emps.add(e);
        }
        Set<Long> empIds = new HashSet<>();
        for (Employee e : emps) empIds.add(e.getId());

        // 原始打卡记录：签到或签退落在范围内都算（覆盖缺卡场景）
        // 注意 OR 条件要整体包在 and() 里，否则会绕过员工范围过滤
        List<CheckinRecord> records = checkinService.list(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .in(!empIds.isEmpty(), CheckinRecord::getEmpId, empIds)
                        .and(w -> w
                                .and(w2 -> w2.ge(CheckinRecord::getSignInTime, startTime).lt(CheckinRecord::getSignInTime, endTime))
                                .or(w2 -> w2.ge(CheckinRecord::getSignOutTime, startTime).lt(CheckinRecord::getSignOutTime, endTime))));

        // 已通过的审批单（只认 approved；待审批/驳回/撤销一律不计入）
        List<ApprovalRecord> approvals = approvalService.list(
                Wrappers.<ApprovalRecord>lambdaQuery()
                        .in(!empIds.isEmpty(), ApprovalRecord::getEmpId, empIds)
                        .eq(ApprovalRecord::getStatus, "approved")
                        .ge(ApprovalRecord::getStartTime, startTime)
                        .lt(ApprovalRecord::getEndTime, endTime));

        // 记录按员工分组
        Map<Long, List<CheckinRecord>> recByEmp = new HashMap<>();
        for (CheckinRecord r : records) recByEmp.computeIfAbsent(r.getEmpId(), k -> new ArrayList<>()).add(r);
        Map<Long, List<ApprovalRecord>> apByEmp = new HashMap<>();
        for (ApprovalRecord a : approvals) apByEmp.computeIfAbsent(a.getEmpId(), k -> new ArrayList<>()).add(a);

        // 场所考勤规则缓存（逐条打卡判定迟到早退要用）
        Map<Long, AttendRule> ruleCache = new HashMap<>();

        // 逐员工统计
        List<Map<String, Object>> empRows = new ArrayList<>();
        Map<String, double[]> deptAgg = new TreeMap<>(); // dept -> [normalHours, overtimeHours]
        int lateTotal = 0, earlyTotal = 0, missingTotal = 0, normalTotal = 0;

        for (Employee e : emps) {
            List<CheckinRecord> rs = recByEmp.getOrDefault(e.getId(), Collections.emptyList());
            List<ApprovalRecord> as = apByEmp.getOrDefault(e.getId(), Collections.emptyList());

            int attendDays = 0, late = 0, early = 0, missing = 0, normal = 0;
            double normalHours = 0;
            Set<LocalDate> days = new HashSet<>();

            for (CheckinRecord r : rs) {
                AttendRule rule = ruleFor(r.getVenueId(), ruleCache);
                boolean hasIn = r.getSignInTime() != null;
                boolean hasOut = r.getSignOutTime() != null;

                // —— 四类独立判定，互不排斥 ——
                if (!hasIn) missing++;              // 缺签到
                if (!hasOut) missing++;             // 缺签退
                boolean isLate = hasIn && r.getSignInTime().toLocalTime()
                        .isAfter(rule.getWorkStart().plusMinutes(rule.getLateMinutes()));
                if (isLate) late++;
                boolean isEarly = hasOut && r.getSignOutTime().toLocalTime().isBefore(rule.getWorkEnd());
                if (isEarly) early++;
                if (hasIn && !isLate) normal++;     // 签到正常（即使后来早退了，这次正常签到也算）

                if (hasIn) days.add(r.getSignInTime().toLocalDate());
                if (hasIn && hasOut) {
                    normalHours += Duration.between(r.getSignInTime(), r.getSignOutTime()).toMinutes() / 60.0;
                }
            }
            attendDays = days.size();

            double overtimeHours = 0, leaveHours = 0, compHours = 0;
            for (ApprovalRecord a : as) {
                double h = PredictService.durationOf(a); // duration_hours优先，为0时按起止时间兜底
                if ("overtime".equals(a.getType())) overtimeHours += h;
                else if (LEAVE_TYPES.contains(a.getType())) leaveHours += h;
                else if ("compensate".equals(a.getType())) compHours += h;
            }

            Map<String, Object> row = new HashMap<>();
            row.put("empId", e.getId());
            row.put("empNo", e.getEmpNo());
            row.put("name", e.getName());
            row.put("dept", e.getDept() == null ? "未分配" : e.getDept());
            row.put("attendDays", attendDays);
            row.put("lateCount", late);
            row.put("earlyCount", early);
            row.put("missingCount", missing);
            row.put("normalHours", round2(normalHours));
            row.put("overtimeHours", round2(overtimeHours));
            row.put("leaveHours", round2(leaveHours));
            row.put("compHours", round2(compHours));
            empRows.add(row);

            lateTotal += late;
            earlyTotal += early;
            missingTotal += missing;
            normalTotal += normal;

            // 部门汇总（柱状图）：正常工时 + 加班时长
            double[] agg = deptAgg.computeIfAbsent(row.get("dept").toString(), k -> new double[2]);
            agg[0] += normalHours;
            agg[1] += overtimeHours;
        }

        empRows.sort(Comparator.comparing(a -> a.get("dept") + a.get("name").toString()));

        // 部门柱状图数据
        List<Map<String, Object>> deptRows = new ArrayList<>();
        for (Map.Entry<String, double[]> en : deptAgg.entrySet()) {
            Map<String, Object> d = new HashMap<>();
            d.put("dept", en.getKey());
            d.put("normalHours", round2(en.getValue()[0]));
            d.put("overtimeHours", round2(en.getValue()[1]));
            deptRows.add(d);
        }

        // 异常占比饼图：7 种互斥状态分布（调用统一判定服务）
        Map<String, Long> anomalyDist = new HashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                    || d.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) continue;
            Map<String, Long> dayDist = statusService.countByDay(emps, d);
            dayDist.forEach((k, v) -> anomalyDist.merge(k, v, Long::sum));
        }
        List<Map<String, Object>> anomaly = new ArrayList<>();
        for (Map.Entry<String, String> en : AttendanceStatusService.CN.entrySet()) {
            anomaly.add(Map.of("name", en.getValue(), "value", anomalyDist.getOrDefault(en.getKey(), 0L)));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("emps", empRows);
        result.put("depts", deptRows);
        result.put("anomaly", anomaly);
        return result;
    }

    /**
     * 数据穿透：某员工某段时间的原始打卡记录 + 审批单据明细
     */
    public Map<String, Object> detail(Long empId, LocalDate start, LocalDate end) {
        LocalDateTime startTime = start.atStartOfDay();
        LocalDateTime endTime = end.plusDays(1).atStartOfDay();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        Map<Long, String> venueNames = new HashMap<>();
        venueService.list().forEach(v -> venueNames.put(v.getId(), v.getName()));

        // 原始打卡明细（按时间排序）
        List<Map<String, Object>> checkins = new ArrayList<>();
        List<CheckinRecord> records = checkinService.list(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(CheckinRecord::getEmpId, empId)
                        .and(w -> w
                                .and(w2 -> w2.ge(CheckinRecord::getSignInTime, startTime).lt(CheckinRecord::getSignInTime, endTime))
                                .or(w2 -> w2.ge(CheckinRecord::getSignOutTime, startTime).lt(CheckinRecord::getSignOutTime, endTime)))
                        .orderByAsc(CheckinRecord::getSignInTime));
        for (CheckinRecord r : records) {
            Map<String, Object> c = new HashMap<>();
            c.put("date", r.getSignInTime() == null
                    ? (r.getSignOutTime() == null ? "" : r.getSignOutTime().toLocalDate().toString())
                    : r.getSignInTime().toLocalDate().toString());
            c.put("signInTime", r.getSignInTime() == null ? "缺卡" : r.getSignInTime().format(f));
            c.put("signOutTime", r.getSignOutTime() == null ? "缺卡" : r.getSignOutTime().format(f));
            c.put("status", statusText(r.getStatus()));
            c.put("venueName", venueNames.getOrDefault(r.getVenueId(), "-"));
            c.put("checkinType", "face".equals(r.getCheckinType()) ? "人脸识别" : "拍照");
            checkins.add(c);
        }

        // 审批单据明细（含未通过的，供追溯；通过的才计入工时）
        List<Map<String, Object>> approvalRows = new ArrayList<>();
        List<ApprovalRecord> approvals = approvalService.list(
                Wrappers.<ApprovalRecord>lambdaQuery()
                        .eq(ApprovalRecord::getEmpId, empId)
                        .ge(ApprovalRecord::getStartTime, startTime.minusDays(30))
                        .lt(ApprovalRecord::getEndTime, endTime)
                        .orderByDesc(ApprovalRecord::getStartTime));
        for (ApprovalRecord a : approvals) {
            Map<String, Object> ap = new HashMap<>();
            ap.put("id", a.getId());
            ap.put("typeText", TYPE_TEXT.getOrDefault(a.getType(), a.getType()));
            ap.put("startTime", a.getStartTime() == null ? "" : a.getStartTime().format(f));
            ap.put("endTime", a.getEndTime() == null ? "" : a.getEndTime().format(f));
            ap.put("durationHours", a.getDurationHours());
            ap.put("reason", a.getReason());
            ap.put("status", approvalStatusText(a.getStatus()));
            approvalRows.add(ap);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("checkins", checkins);
        result.put("approvals", approvalRows);
        return result;
    }

    /** 导出Excel：全员考勤汇总表 */
    public byte[] export(String dept, Long venueId, LocalDate start, LocalDate end) {
        Map<String, Object> data = summary(dept, venueId, start, end);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> emps = (List<Map<String, Object>>) data.get("emps");

        String[] headers = {"工号", "姓名", "部门", "出勤天数", "迟到次数", "早退次数",
                "缺卡次数", "正常工时(h)", "加班时长(h)", "请假时长(h)", "调休时长(h)"};
        List<Object[]> rows = new ArrayList<>();
        for (Map<String, Object> e : emps) {
            rows.add(new Object[]{e.get("empNo"), e.get("name"), e.get("dept"),
                    e.get("attendDays"), e.get("lateCount"), e.get("earlyCount"), e.get("missingCount"),
                    e.get("normalHours"), e.get("overtimeHours"), e.get("leaveHours"), e.get("compHours")});
        }
        return ExcelUtil.build("考勤工时汇总", headers, rows);
    }

    /** 全部部门名（全局筛选下拉用） */
    public List<String> allDepts() {
        List<String> depts = new ArrayList<>();
        for (Employee e : employeeService.list()) depts.add(e.getDept());
        return depts;
    }

    /** 取某场所的考勤规则（带缓存），RuleService 对未配置场所已内置默认 9:00-18:00 宽限10分钟 */
    private AttendRule ruleFor(Long venueId, Map<Long, AttendRule> cache) {
        if (venueId == null) return defaultRule();
        return cache.computeIfAbsent(venueId, ruleService::getByVenueId);
    }

    private static AttendRule defaultRule() {
        AttendRule r = new AttendRule();
        r.setWorkStart(DEFAULT_START);
        r.setWorkEnd(DEFAULT_END);
        r.setLateMinutes(DEFAULT_GRACE);
        return r;
    }

    private static String statusText(String s) {
        if (s == null) return "正常";
        return switch (s) {
            case "late" -> "迟到";
            case "early" -> "早退";
            case "normal" -> "正常";
            default -> s;
        };
    }

    private static String approvalStatusText(String s) {
        if (s == null) return "-";
        return switch (s) {
            case "pending" -> "待审批";
            case "approved" -> "已通过";
            case "rejected" -> "已驳回";
            case "cancelled" -> "已撤销";
            default -> s;
        };
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
