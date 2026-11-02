package com.lianzhi.attendance.modules.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lianzhi.attendance.modules.approval.entity.ApprovalRecord;
import com.lianzhi.attendance.modules.approval.service.ApprovalService;
import com.lianzhi.attendance.modules.checkin.entity.CheckinRecord;
import com.lianzhi.attendance.modules.checkin.service.CheckinService;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.service.EmployeeService;
import com.lianzhi.attendance.modules.rule.entity.AttendRule;
import com.lianzhi.attendance.modules.rule.service.RuleService;
import com.lianzhi.attendance.modules.venue.entity.Venue;
import com.lianzhi.attendance.modules.venue.service.VenueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 规则化 Phase 1 —— AI 问答 Agent
 * 不依赖外部 LLM / 向量库，基于关键词意图识别 + 查数据库 + 模板生成自然语言回答。
 * 后续可无缝升级为 RAG（替换 ask() 内部实现即可，对前端透明）。
 */
@Slf4j
@Service
public class AiAgentService {

    @Autowired CheckinService checkinService;
    @Autowired EmployeeService employeeService;
    @Autowired RuleService ruleService;
    @Autowired VenueService venueService;
    @Autowired ApprovalService approvalService;

    private final ObjectMapper json = new ObjectMapper();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Data @AllArgsConstructor
    public static class Answer {
        private String content;
        /** JSON 字符串：来源引用 */
        private String sources;
    }

    /**
     * @param question  用户问题
     * @param userType  admin / h5
     * @param userId    会话所属用户 ID
     * @param empId     H5 端可选：绑定的员工 ID（查个人考勤用）
     */
    public Answer ask(String question, String userType, Long userId, Long empId) {
        String q = question.trim();
        List<Map<String, String>> sources = new ArrayList<>();
        String reply = route(q, userType, userId, empId, sources);

        try {
            return new Answer(reply, sources.isEmpty() ? null : json.writeValueAsString(sources));
        } catch (Exception e) {
            return new Answer(reply, null);
        }
    }

    /** 意图路由 */
    private String route(String q, String userType, Long userId, Long empId, List<Map<String, String>> sources) {

        // —— 1. 考勤规则 / 上下班时间 ——
        if (containsAny(q, "上班时间", "几点上班", "下班时间", "几点下班", "考勤规则", "宽限", "迟到几分钟", "迟到多久算")) {
            return ruleQuery(q, sources);
        }

        // —— 2. 今日考勤概况（管理员视角）——
        if (containsAny(q, "今日考勤", "今天考勤", "今日概况", "今天概况", "今天打卡", "今日打卡")) {
            return todayOverview(sources);
        }

        // —— 3. 迟到 / 早退 / 缺勤人员 ——
        if (containsAny(q, "迟到", "谁迟到", "迟到人员", "今天迟到", "本月迟到", "早退", "缺勤", "没打卡", "未打卡")) {
            return statusQuery(q, sources);
        }

        // —— 4. 部门工时 ——
        if (containsAny(q, "部门", "各部门", "工时", "部门统计", "部门分析")) {
            return deptHours(q, sources);
        }

        // —— 5. 员工查询（打卡状态 / 考勤）——
        if (containsAny(q, "员工", "谁", "某员工") || looksLikeNameQuery(q)) {
            return employeeQuery(q, sources);
        }

        // —— 6. 请假 / 审批 ——
        if (containsAny(q, "请假", "申请", "审批", "年假", "病假", "加班", "待审批")) {
            return approvalQuery(q, userType, userId, empId, sources);
        }

        // —— 7. 场所 / 地点 ——
        if (containsAny(q, "场所", "地点", "办公点", "办公场所", "公司在哪")) {
            return venueQuery(sources);
        }

        // —— 8. 本月个人统计（H5 用户常问）——
        if ((userType.equals("h5") || empId != null) && containsAny(q, "本月", "我这个月", "我的考勤", "我的统计", "我这个月加班", "这个月我")) {
            return personalMonth(empId, sources);
        }

        // —— 默认兜底 ——
        return fallback();
    }

    // ===================== 各意图处理 =====================

    /** 考勤规则问答 */
    private String ruleQuery(String q, List<Map<String, String>> sources) {
        List<AttendRule> rules = ruleService.list();
        if (rules.isEmpty()) {
            // 默认规则
            AttendRule def = new AttendRule();
            def.setWorkStart(LocalTime.of(9, 0));
            def.setWorkEnd(LocalTime.of(18, 0));
            def.setLateMinutes(10);
            rules.add(def);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("📋 当前配置的考勤规则如下：\n\n");
        Set<Long> seenVenue = new HashSet<>();
        for (AttendRule r : rules) {
            String vName = "默认规则";
            if (r.getVenueId() != null && !seenVenue.contains(r.getVenueId())) {
                seenVenue.add(r.getVenueId());
                Venue v = venueService.getById(r.getVenueId());
                if (v != null) vName = v.getName();
            }
            sb.append(String.format("• **%s**：上班 %s，下班 %s，迟到宽限 %d 分钟\n",
                    vName,
                    r.getWorkStart() != null ? r.getWorkStart().toString() : "09:00",
                    r.getWorkEnd() != null ? r.getWorkEnd().toString() : "18:00",
                    r.getLateMinutes() != null ? r.getLateMinutes() : 10));
        }
        sources.add(src("attend_rule", "场所考勤规则配置"));
        return sb.toString();
    }

    /** 今日考勤概况 */
    private String todayOverview(List<Map<String, String>> sources) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        long totalEmployees = employeeService.count(Wrappers.<Employee>lambdaQuery().eq(Employee::getStatus, 1));
        List<CheckinRecord> todayRecords = checkinService.list(Wrappers.<CheckinRecord>lambdaQuery()
                .ge(CheckinRecord::getSignInTime, start).lt(CheckinRecord::getSignInTime, end));

        long checkedIn = todayRecords.stream().map(CheckinRecord::getEmpId).distinct().count();
        long lateCount = todayRecords.stream().filter(r ->
                "late".equals(r.getStatus()) || "lateEarly".equals(r.getStatus())).count();
        long earlyCount = todayRecords.stream().filter(r ->
                "early".equals(r.getStatus()) || "lateEarly".equals(r.getStatus())).count();

        StringBuilder sb = new StringBuilder();
        sb.append("📅 **").append(today.format(DateTimeFormatter.ISO_DATE)).append(" 考勤概况**\n\n");
        sb.append(String.format("• 在职员工：**%d** 人\n", totalEmployees));
        sb.append(String.format("• 已打卡：**%d** 人\n", checkedIn));
        sb.append(String.format("• 未打卡：**%d** 人\n", Math.max(totalEmployees - checkedIn, 0)));
        sb.append(String.format("• 迟到：**%d** 人次\n", lateCount));
        sb.append(String.format("• 早退：**%d** 人次\n", earlyCount));

        // 列出迟到人员
        List<CheckinRecord> lates = todayRecords.stream().filter(r -> "late".equals(r.getStatus())).collect(Collectors.toList());
        if (!lates.isEmpty()) {
            sb.append("\n**迟到人员**：\n");
            appendEmpNames(sb, lates, "签到时间");
        }

        sources.add(src("checkin_record", "今日打卡数据"));
        sources.add(src("employee", "员工总数"));
        return sb.toString();
    }

    /** 迟到 / 早退 / 缺勤查询 */
    private String statusQuery(String q, List<Map<String, String>> sources) {
        LocalDateTime start;
        LocalDateTime end;
        String period;

        if (containsAny(q, "本月", "这个月")) {
            YearMonth ym = YearMonth.now();
            start = ym.atDay(1).atStartOfDay();
            end = ym.plusMonths(1).atDay(1).atStartOfDay();
            period = ym.toString();
        } else {
            LocalDate today = LocalDate.now();
            start = today.atStartOfDay();
            end = today.plusDays(1).atStartOfDay();
            period = today.format(DateTimeFormatter.ISO_DATE);
        }

        List<CheckinRecord> records = checkinService.list(Wrappers.<CheckinRecord>lambdaQuery()
                .ge(CheckinRecord::getSignInTime, start).lt(CheckinRecord::getSignInTime, end));

        StringBuilder sb = new StringBuilder();
        sb.append("📋 **").append(period).append(" 考勤异常**\n\n");

        if (containsAny(q, "迟到")) {
            List<CheckinRecord> lates = records.stream().filter(r -> "late".equals(r.getStatus())).collect(Collectors.toList());
            sb.append(String.format("🚨 迟到 **%d** 人次\n", lates.size()));
            if (!lates.isEmpty()) appendEmpNames(sb, lates, "签到时间");
        }
        if (containsAny(q, "早退")) {
            List<CheckinRecord> earlys = records.stream().filter(r -> "early".equals(r.getStatus())).collect(Collectors.toList());
            sb.append(String.format("\n⚠️ 早退 **%d** 人次\n", earlys.size()));
            if (!earlys.isEmpty()) appendEmpNames(sb, earlys, "签退时间");
        }
        if (containsAny(q, "未打卡", "没打卡", "缺勤")) {
            List<Employee> all = employeeService.list(Wrappers.<Employee>lambdaQuery().eq(Employee::getStatus, 1));
            Set<Long> checkedEmpIds = records.stream().map(CheckinRecord::getEmpId).collect(Collectors.toSet());
            List<Employee> absents = all.stream().filter(e -> !checkedEmpIds.contains(e.getId())).collect(Collectors.toList());
            sb.append(String.format("\n❌ 未打卡 **%d** 人\n", absents.size()));
            if (!absents.isEmpty()) {
                for (Employee e : absents) {
                    sb.append(String.format("• %s（%s）\n", e.getName(), e.getDept() != null ? e.getDept() : "未分配部门"));
                }
            }
        }

        sources.add(src("checkin_record", "考勤异常数据"));
        return sb.toString();
    }

    /** 部门工时分析 */
    private String deptHours(String q, List<Map<String, String>> sources) {
        LocalDateTime start;
        LocalDateTime end;
        String period;

        if (containsAny(q, "本月", "这个月")) {
            YearMonth ym = YearMonth.now();
            start = ym.atDay(1).atStartOfDay();
            end = ym.plusMonths(1).atDay(1).atStartOfDay();
            period = ym.toString();
        } else {
            LocalDate today = LocalDate.now();
            start = today.atStartOfDay();
            end = today.plusDays(1).atStartOfDay();
            period = today.format(DateTimeFormatter.ISO_DATE);
        }

        List<CheckinRecord> records = checkinService.list(Wrappers.<CheckinRecord>lambdaQuery()
                .ge(CheckinRecord::getSignInTime, start).lt(CheckinRecord::getSignInTime, end));

        // 预加载员工
        List<Long> empIds = records.stream().map(CheckinRecord::getEmpId).distinct().collect(Collectors.toList());
        Map<Long, Employee> empMap = employeeService.listByIds(empIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        // 按部门聚合
        Map<String, DeptStat> deptMap = new LinkedHashMap<>();
        for (CheckinRecord r : records) {
            Employee e = empMap.get(r.getEmpId());
            if (e == null) continue;
            String dept = e.getDept() != null ? e.getDept() : "未分配部门";
            DeptStat stat = deptMap.computeIfAbsent(dept, k -> new DeptStat(dept));
            stat.empIds.add(r.getEmpId());
            if (r.getSignInTime() != null && r.getSignOutTime() != null) {
                long mins = Duration.between(r.getSignInTime(), r.getSignOutTime()).toMinutes();
                stat.totalMinutes += mins;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📊 **").append(period).append(" 部门工时分析**\n\n");
        if (deptMap.isEmpty()) {
            sb.append("暂无打卡数据。\n");
        } else {
            // 按总工时排序
            List<DeptStat> sorted = deptMap.values().stream()
                    .sorted((a, b) -> Long.compare(b.totalMinutes, a.totalMinutes))
                    .collect(Collectors.toList());
            for (DeptStat ds : sorted) {
                BigDecimal totalH = BigDecimal.valueOf(ds.totalMinutes).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
                BigDecimal avgH = ds.empIds.isEmpty() ? BigDecimal.ZERO :
                        totalH.divide(BigDecimal.valueOf(ds.empIds.size()), 1, RoundingMode.HALF_UP);
                sb.append(String.format("**%s**：总工时 %s h，人均 %s h（%d 人参与）\n",
                        ds.name, totalH, avgH, ds.empIds.size()));
            }
        }

        sources.add(src("checkin_record", "打卡工时数据"));
        sources.add(src("employee", "员工归属部门"));
        return sb.toString();
    }

    /** 员工查询 */
    private String employeeQuery(String q, List<Map<String, String>> sources) {
        // 尝试提取名字关键词
        String nameKey = extractNameKeyword(q);
        LambdaQueryWrapper<Employee> w = Wrappers.<Employee>lambdaQuery().eq(Employee::getStatus, 1);
        if (nameKey != null && !nameKey.isEmpty()) {
            w.like(Employee::getName, nameKey);
        }
        List<Employee> employees = employeeService.list(w);

        if (employees.isEmpty()) {
            return "抱歉，没找到匹配的员工信息。\n你可以问「今天迟到的人是谁」或「销售部有哪些人」。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("👥 查到 **").append(employees.size()).append("** 位员工：\n\n");
        for (Employee e : employees) {
            sb.append(String.format("• **%s**｜工号 %s｜%s｜%s｜%s\n",
                    e.getName(),
                    e.getEmpNo() != null ? e.getEmpNo() : "-",
                    e.getGender() != null ? e.getGender() : "-",
                    e.getDept() != null ? e.getDept() : "未分配部门",
                    e.getPhone() != null ? e.getPhone() : "-"));
        }

        sources.add(src("employee", "员工档案"));
        return sb.toString();
    }

    /** 请假 / 审批查询 */
    private String approvalQuery(String q, String userType, Long userId, Long empId, List<Map<String, String>> sources) {
        List<ApprovalRecord> records;
        if (userType.equals("h5") && empId != null) {
            records = approvalService.list(Wrappers.<ApprovalRecord>lambdaQuery()
                    .eq(ApprovalRecord::getEmpId, empId)
                    .orderByDesc(ApprovalRecord::getCreateTime)
                    .last("LIMIT 10"));
        } else {
            records = approvalService.list(Wrappers.<ApprovalRecord>lambdaQuery()
                    .orderByDesc(ApprovalRecord::getCreateTime)
                    .last("LIMIT 10"));
        }

        if (records.isEmpty()) {
            return "📝 当前没有审批记录。\n员工可以在「个人考勤 → 发起申请」里提交请假、加班等申请。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📝 最近 **").append(records.size()).append("** 条审批记录：\n\n");
        for (ApprovalRecord r : records) {
            String typeText = approvalTypeText(r.getType());
            String statusText = approvalStatusText(r.getStatus());
            String empName = "-";
            Employee emp = employeeService.getById(r.getEmpId());
            if (emp != null) empName = emp.getName();
            sb.append(String.format("• %s｜**%s**｜%s → %s（%s）｜%s\n",
                    typeText, empName,
                    r.getStartTime() != null ? r.getStartTime().format(FMT) : "-",
                    r.getEndTime() != null ? r.getEndTime().format(FMT) : "-",
                    r.getDurationHours() != null ? r.getDurationHours() + "h" : "-",
                    statusText));
        }

        sources.add(src("approval_record", "审批记录"));
        return sb.toString();
    }

    /** 场所查询 */
    private String venueQuery(List<Map<String, String>> sources) {
        List<Venue> venues = venueService.list(Wrappers.<Venue>lambdaQuery().eq(Venue::getStatus, 1));
        if (venues.isEmpty()) return "当前没有配置办公场所。";

        StringBuilder sb = new StringBuilder();
        sb.append("🏢 已配置的办公场所：\n\n");
        for (Venue v : venues) {
            sb.append(String.format("• **%s**｜%s｜📍 %s\n",
                    v.getName(),
                    v.getCompanyName() != null ? v.getCompanyName() : "-",
                    v.getAddress() != null ? v.getAddress() : "-"));
        }

        sources.add(src("venue", "办公场所列表"));
        return sb.toString();
    }

    /** H5 用户本月个人统计 */
    private String personalMonth(Long empId, List<Map<String, String>> sources) {
        if (empId == null) return "请先完成员工信息采集后再查询个人考勤。";

        YearMonth ym = YearMonth.now();
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();

        List<CheckinRecord> records = checkinService.list(Wrappers.<CheckinRecord>lambdaQuery()
                .eq(CheckinRecord::getEmpId, empId)
                .ge(CheckinRecord::getSignInTime, start).lt(CheckinRecord::getSignInTime, end));

        long attendDays = records.stream().filter(r -> r.getSignInTime() != null).count();
        long lateCount = records.stream().filter(r ->
                "late".equals(r.getStatus()) || "lateEarly".equals(r.getStatus())).count();
        long earlyCount = records.stream().filter(r ->
                "early".equals(r.getStatus()) || "lateEarly".equals(r.getStatus())).count();
        double totalHours = 0;
        for (CheckinRecord r : records) {
            if (r.getSignInTime() != null && r.getSignOutTime() != null) {
                long mins = Duration.between(r.getSignInTime(), r.getSignOutTime()).toMinutes();
                totalHours += mins / 60.0;
            }
        }

        Employee emp = employeeService.getById(empId);
        String empName = emp != null ? emp.getName() : "你";

        StringBuilder sb = new StringBuilder();
        sb.append("📅 **").append(empName).append(" · ").append(ym.toString()).append(" 个人考勤**\n\n");
        sb.append(String.format("• 出勤天数：**%d** 天\n", attendDays));
        sb.append(String.format("• 迟到 **%d** 次\n", lateCount));
        sb.append(String.format("• 早退 **%d** 次\n", earlyCount));
        sb.append(String.format("• 累计工时：**%.2f** 小时\n", totalHours));

        sources.add(src("checkin_record", "个人月度打卡数据"));
        return sb.toString();
    }

    /** 兜底 */
    private String fallback() {
        return """
                抱歉，我暂时没太理解你的问题 😅

                你可以这样问我：
                • 今日考勤概况
                • 今天谁迟到了
                • 公司上班时间是几点
                • 销售部这个月工时多少
                • 最近有哪些请假申请
                • 本月我的考勤统计

                （当前为规则化 Phase 1 模式，后续会接入 RAG 知识库）
                """;
    }

    // ===================== 工具方法 =====================

    private static boolean containsAny(String s, String... kws) {
        for (String k : kws) if (s.contains(k)) return true;
        return false;
    }

    private static boolean looksLikeNameQuery(String s) {
        // 包含"叫"或"是"或"谁"后面跟一个 2-3 字的中文
        return s.matches(".*叫.+.") || s.matches(".*是.+") || s.matches(".*查.+");
    }

    private static String extractNameKeyword(String s) {
        // 去掉疑问词，剩下的字可能就是名字
        String cleaned = s.replaceAll("[今天|这个月|本月|谁|查询|查|的|员工|人员|人员|情况|状态]", "").trim();
        // 如果剩下 2-4 字，可能是名字
        if (cleaned.length() >= 2 && cleaned.length() <= 4) return cleaned;
        return null;
    }

    private static Map<String, String> src(String table, String desc) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("table", table);
        m.put("desc", desc);
        m.put("retrieved_at", LocalDateTime.now().format(FMT));
        return m;
    }

    private void appendEmpNames(StringBuilder sb, List<CheckinRecord> records, String timeField) {
        List<Long> empIds = records.stream().map(CheckinRecord::getEmpId).distinct().collect(Collectors.toList());
        Map<Long, Employee> empMap = employeeService.listByIds(empIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
        for (CheckinRecord r : records) {
            Employee e = empMap.get(r.getEmpId());
            String name = e != null ? e.getName() : "员工" + r.getEmpId();
            String dept = e != null && e.getDept() != null ? "（" + e.getDept() + "）" : "";
            LocalDateTime t = "签退时间".equals(timeField) ? r.getSignOutTime() : r.getSignInTime();
            sb.append(String.format("• %s%s · %s\n", name, dept,
                    t != null ? t.format(FMT) : "-"));
        }
    }

    private static String approvalTypeText(String type) {
        if (type == null) return "申请";
        return switch (type) {
            case "leave" -> "请假";
            case "sick" -> "病假";
            case "annual" -> "年假";
            case "overtime" -> "加班";
            case "compensate" -> "补休";
            case "makeup" -> "补卡";
            case "resign" -> "离职";
            default -> type;
        };
    }

    private static String approvalStatusText(String status) {
        if (status == null) return "-";
        return switch (status) {
            case "pending" -> "⏳ 待审批";
            case "approved" -> "✅ 已通过";
            case "rejected" -> "❌ 已驳回";
            case "cancelled" -> "🚫 已撤回";
            default -> status;
        };
    }

    // —— 内部辅助类 ——
    private static class DeptStat {
        String name;
        Set<Long> empIds = new HashSet<>();
        long totalMinutes = 0;
        DeptStat(String n) { this.name = n; }
    }
}
