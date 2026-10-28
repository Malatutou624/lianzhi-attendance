package com.lianzhi.attendance.modules.stats.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.analysis.service.AttendanceStatusService;
import com.lianzhi.attendance.modules.analysis.service.PredictService;
import com.lianzhi.attendance.modules.analysis.util.ExcelUtil;
import com.lianzhi.attendance.modules.approval.entity.ApprovalRecord;
import com.lianzhi.attendance.modules.approval.service.ApprovalService;
import com.lianzhi.attendance.modules.checkin.entity.CheckinRecord;
import com.lianzhi.attendance.modules.checkin.service.CheckinService;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.service.EmployeeService;
import com.lianzhi.attendance.modules.venue.entity.Venue;
import com.lianzhi.attendance.modules.venue.service.VenueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 首页看板统计接口
 * 所有考勤状态判定统一委托给 AttendanceStatusService（7 种互斥状态口径）
 */
@RestController
@RequestMapping("/stats")
public class StatsController {

    @Autowired private EmployeeService employeeService;
    @Autowired private CheckinService checkinService;
    @Autowired private VenueService venueService;
    @Autowired private ApprovalService approvalService;
    @Autowired private AttendanceStatusService statusService;

    /** 获取场所过滤后的在职员工列表 */
    private List<Employee> active(Long venueId) {
        return employeeService.list(
                Wrappers.<Employee>lambdaQuery()
                        .eq(Employee::getStatus, 1)
                        .eq(venueId != null, Employee::getVenueId, venueId));
    }

    /**
     * 今日概览卡片（4 个指标）
     *   empTotal      在职员工数
     *   attendanceRate 出勤率 = 已出勤人数(今天有签到) / 应出勤人数(在职-请假) × 100%
     *   avgWorkHours   平均有效工时 = 当天总有效工时 / 出勤人数
     *                  有效工时：已签退 = 签到到签退；未签退 = 签到到当前时刻（实时）
     *   abnormalRate   异常率 = (在职员工数 - 正常签到签退人数) / 在职员工数 × 100%
     */
    @GetMapping("/today")
    public Result<Map<String, Object>> today(@RequestParam(required = false) Long venueId) {
        LocalDate today = LocalDate.now();
        List<Employee> emps = active(venueId);
        Map<String, Long> dist = statusService.countByDay(emps, today);

        long empTotal = emps.size();
        long leaveCount = dist.getOrDefault(AttendanceStatusService.LEAVE, 0L);
        long normalCount = dist.getOrDefault(AttendanceStatusService.NORMAL, 0L);

        // 出勤人数 = 今天有签到记录的员工数
        List<CheckinRecord> todayRecords = checkinService.list(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .ge(CheckinRecord::getSignInTime, today.atStartOfDay())
                        .eq(venueId != null, CheckinRecord::getVenueId, venueId));
        Set<Long> attendedIds = todayRecords.stream()
                .filter(r -> r.getSignInTime() != null)
                .map(CheckinRecord::getEmpId).collect(Collectors.toSet());

        // 出勤率
        long expectedAttend = empTotal - leaveCount;
        double attendanceRate = expectedAttend > 0 ? attendedIds.size() * 100.0 / expectedAttend : 0.0;

        // 平均有效工时（实时）：已签退算签到到签退，未签退算签到到当前时刻
        LocalDateTime now = LocalDateTime.now();
        double totalHours = 0;
        for (CheckinRecord r : todayRecords) {
            if (r.getSignInTime() == null) continue;
            LocalDateTime end = r.getSignOutTime() != null ? r.getSignOutTime() : now;
            if (end.isAfter(r.getSignInTime())) {
                totalHours += Duration.between(r.getSignInTime(), end).toMinutes() / 60.0;
            }
        }
        double avgWorkHours = attendedIds.isEmpty() ? 0.0 : totalHours / attendedIds.size();

        // 异常率：正常签到签退之外都算异常（含请假、旷工、迟到、早退等）
        double abnormalRate = empTotal > 0 ? (empTotal - normalCount) * 100.0 / empTotal : 0.0;

        Map<String, Object> data = new HashMap<>();
        data.put("empTotal", empTotal);
        data.put("attended", attendedIds.size());
        data.put("expectedAttend", expectedAttend);
        data.put("attendanceRate", round1(attendanceRate));
        data.put("avgWorkHours", round2(avgWorkHours));
        data.put("abnormalRate", round1(abnormalRate));
        return Result.ok(data);
    }

    /** 打卡趋势：日/周/月/年 */
    @GetMapping("/trend")
    public Result<Map<String, Object>> trend(@RequestParam(defaultValue = "day") String type,
                                             @RequestParam(required = false) Long venueId) {
        LocalDate today = LocalDate.now();
        DateTimeFormatter md = DateTimeFormatter.ofPattern("MM-dd");
        List<String> xAxis = new ArrayList<>();
        List<Long> data = new ArrayList<>();

        switch (type) {
            case "week" -> {
                for (int i = 3; i >= 0; i--) {
                    LocalDate ws = today.with(DayOfWeek.MONDAY).minusWeeks(i);
                    LocalDate we = ws.plusDays(7);
                    xAxis.add(ws.format(md) + "~" + we.minusDays(1).format(md));
                    data.add(countCheckin(ws.atStartOfDay(), we.atStartOfDay(), venueId));
                }
            }
            case "month" -> {
                for (int i = 5; i >= 0; i--) {
                    YearMonth ym = YearMonth.from(today).minusMonths(i);
                    xAxis.add(ym.toString());
                    data.add(countCheckin(ym.atDay(1).atStartOfDay(), ym.plusMonths(1).atDay(1).atStartOfDay(), venueId));
                }
            }
            case "year" -> {
                for (int i = 2; i >= 0; i--) {
                    int y = today.getYear() - i;
                    xAxis.add(y + "年");
                    data.add(countCheckin(LocalDate.of(y, 1, 1).atStartOfDay(),
                            LocalDate.of(y + 1, 1, 1).atStartOfDay(), venueId));
                }
            }
            default -> {
                for (int i = 6; i >= 0; i--) {
                    LocalDate d = today.minusDays(i);
                    xAxis.add(d.format(md));
                    data.add(countCheckin(d.atStartOfDay(), d.plusDays(1).atStartOfDay(), venueId));
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("xAxis", xAxis);
        result.put("data", data);
        return Result.ok(result);
    }

    /** 今日打卡状态分布（饼图）—— 7 种互斥状态 */
    @GetMapping("/todayStatus")
    public Result<List<Map<String, Object>>> todayStatus(@RequestParam(required = false) Long venueId) {
        Map<String, Long> dist = statusService.countByDay(active(venueId), LocalDate.now());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, String> en : AttendanceStatusService.CN.entrySet()) {
            result.add(Map.of("name", en.getValue(), "value", dist.getOrDefault(en.getKey(), 0L)));
        }
        return Result.ok(result);
    }

    /**
     * 人员列表
     * type: active在职 / notAttended未出勤(未请假且未打卡) / workHoursRank今日工时排名 /
     *       abnormal出勤异常 / checked / late / early / lateEarly / leave / absent / other
     */
    @GetMapping("/people")
    public Result<List<Map<String, Object>>> people(@RequestParam String type,
                                                    @RequestParam(required = false) Long venueId) {
        return Result.ok(peopleData(type, venueId));
    }

    /**
     * 人员明细导出Excel（未出勤 / 出勤异常 卡片用）
     */
    @GetMapping("/people/export")
    public ResponseEntity<byte[]> peopleExport(@RequestParam String type,
                                               @RequestParam(required = false) Long venueId) {
        List<Map<String, Object>> list = peopleData(type, venueId);
        boolean rank = "workHoursRank".equals(type);
        String[] headers = rank
                ? new String[]{"排名", "工号", "姓名", "部门", "今日工时(h)", "签到时间"}
                : new String[]{"工号", "姓名", "部门", "职位", "状态", "签到时间"};
        List<Object[]> rows = new ArrayList<>();
        for (Map<String, Object> m : list) {
            if (rank) {
                rows.add(new Object[]{m.get("rank"), m.get("empNo"), m.get("name"),
                        m.get("dept"), m.get("hours"),
                        m.get("checkinTime") == null ? "-" : m.get("checkinTime")});
            } else {
                rows.add(new Object[]{m.get("empNo"), m.get("name"), m.get("dept"), m.get("position"),
                        m.get("statusText") == null ? "-" : m.get("statusText"),
                        m.get("checkinTime") == null ? "-" : m.get("checkinTime")});
            }
        }
        String sheet = switch (type) {
            case "notAttended" -> "未出勤人员";
            case "abnormal" -> "出勤异常人员";
            case "workHoursRank" -> "员工工时排名";
            default -> "人员列表";
        };
        return file(ExcelUtil.build(sheet, headers, rows), sheet + ".xlsx");
    }

    /** 人员明细数据（列表与导出共用同一口径） */
    private List<Map<String, Object>> peopleData(String type, Long venueId) {
        LocalDate today = LocalDate.now();
        List<Employee> emps = active(venueId);
        Map<Long, String> venueMap = venueService.list().stream()
                .collect(Collectors.toMap(Venue::getId, Venue::getName));

        switch (type) {
            case "active": {
                List<Map<String, Object>> list = new ArrayList<>();
                for (Employee e : emps) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", e.getId());
                    item.put("empNo", e.getEmpNo());
                    item.put("name", e.getName());
                    item.put("dept", e.getDept());
                    item.put("position", e.getPosition());
                    item.put("venueName", venueMap.get(e.getVenueId()));
                    list.add(item);
                }
                return list;
            }
            case "notAttended": {
                // 未请假且未出勤 = 旷工状态（AttendanceStatusService 的 ABSENT 已排除请假）
                List<Map<String, Object>> list = statusService.peopleByStatus(emps, today, AttendanceStatusService.ABSENT);
                list.forEach(m -> {
                    m.put("venueName", venueMap.get(m.get("venueId")));
                    m.remove("venueId");
                });
                return list;
            }
            case "abnormal": {
                // 出勤异常 = 状态不是"正常"的所有人（请假/旷工/迟到/早退/迟到且早退/其他），标明异常状态
                List<Map<String, Object>> list = statusService.peopleByStatus(emps, today, null).stream()
                        .filter(m -> !AttendanceStatusService.NORMAL.equals(m.get("status")))
                        .toList();
                list.forEach(m -> {
                    m.put("venueName", venueMap.get(m.get("venueId")));
                    m.remove("venueId");
                });
                return list;
            }
            case "workHoursRank": {
                // 今日工时排名：已签退 = 签到到签退；未签退 = 签到到当前时刻（实时）
                List<CheckinRecord> todayRecords = checkinService.list(
                        Wrappers.<CheckinRecord>lambdaQuery()
                                .ge(CheckinRecord::getSignInTime, today.atStartOfDay())
                                .eq(venueId != null, CheckinRecord::getVenueId, venueId));
                Map<Long, Employee> empById = emps.stream()
                        .collect(Collectors.toMap(Employee::getId, e -> e));
                Map<Long, Double> hoursMap = new HashMap<>();
                Map<Long, String> firstInMap = new HashMap<>();
                DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                LocalDateTime now = LocalDateTime.now();
                for (CheckinRecord r : todayRecords) {
                    if (r.getSignInTime() == null || !empById.containsKey(r.getEmpId())) continue;
                    LocalDateTime end = r.getSignOutTime() != null ? r.getSignOutTime() : now;
                    if (end.isAfter(r.getSignInTime())) {
                        hoursMap.merge(r.getEmpId(), Duration.between(r.getSignInTime(), end).toMinutes() / 60.0, Double::sum);
                    }
                    firstInMap.putIfAbsent(r.getEmpId(), r.getSignInTime().format(f));
                }
                List<Map<String, Object>> list = new ArrayList<>();
                final int[] rank = {0};
                hoursMap.entrySet().stream()
                        .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                        .forEach(en -> {
                            Employee e = empById.get(en.getKey());
                            Map<String, Object> item = new HashMap<>();
                            item.put("rank", ++rank[0]);
                            item.put("empNo", e.getEmpNo());
                            item.put("name", e.getName());
                            item.put("dept", e.getDept());
                            item.put("hours", round2(en.getValue()));
                            item.put("checkinTime", firstInMap.get(en.getKey()));
                            list.add(item);
                        });
                return list;
            }
            default: {
                // checked = 非旷工非请假；其它（late/early/lateEarly/leave/absent/other）直接按状态匹配
                String target = "checked".equals(type) ? null : type;
                Set<String> exclude = "checked".equals(type)
                        ? Set.of(AttendanceStatusService.ABSENT, AttendanceStatusService.LEAVE) : null;
                List<Map<String, Object>> list = statusService.peopleByStatus(emps, today, target).stream()
                        .filter(m -> exclude == null || !exclude.contains(m.get("status")))
                        .toList();
                list.forEach(m -> {
                    m.put("venueName", venueMap.get(m.get("venueId")));
                    m.remove("venueId");
                });
                return list;
            }
        }
    }

    /** 部门工时报告（含加班时长） */
    @GetMapping("/workHours")
    public Result<List<Map<String, Object>>> workHours(@RequestParam(required = false) String start,
                                                       @RequestParam(required = false) String end,
                                                       @RequestParam(required = false) Long venueId) {
        LocalDate s = (start == null || start.isEmpty())
                ? LocalDate.now().withDayOfMonth(1) : LocalDate.parse(start);
        LocalDate e = (end == null || end.isEmpty()) ? LocalDate.now() : LocalDate.parse(end);

        List<CheckinRecord> records = checkinService.list(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .ge(CheckinRecord::getSignInTime, s.atStartOfDay())
                        .lt(CheckinRecord::getSignInTime, e.plusDays(1).atStartOfDay())
                        .eq(venueId != null, CheckinRecord::getVenueId, venueId)
                        .isNotNull(CheckinRecord::getSignOutTime));

        Map<Long, Employee> empMap = employeeService.list().stream()
                .collect(Collectors.toMap(Employee::getId, emp -> emp));

        Map<String, List<CheckinRecord>> deptMap = new HashMap<>();
        for (CheckinRecord r : records) {
            Employee emp = empMap.get(r.getEmpId());
            if (emp == null || emp.getDept() == null || emp.getDept().isEmpty()) continue;
            deptMap.computeIfAbsent(emp.getDept(), k -> new ArrayList<>()).add(r);
        }

        List<ApprovalRecord> overtimeList = approvalService.list(
                Wrappers.<ApprovalRecord>lambdaQuery()
                        .eq(ApprovalRecord::getStatus, "approved")
                        .eq(ApprovalRecord::getType, "overtime")
                        .ge(ApprovalRecord::getStartTime, s.atStartOfDay())
                        .lt(ApprovalRecord::getEndTime, e.plusDays(1).atStartOfDay()));
        Map<String, Double> deptOvertime = new HashMap<>();
        for (ApprovalRecord a : overtimeList) {
            Employee emp = empMap.get(a.getEmpId());
            if (emp == null || emp.getDept() == null) continue;
            if (venueId != null && !venueId.equals(emp.getVenueId())) continue;
            deptOvertime.merge(emp.getDept(), PredictService.durationOf(a), Double::sum);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> existing = new HashSet<>();
        for (Map.Entry<String, List<CheckinRecord>> entry : deptMap.entrySet()) {
            String dept = entry.getKey();
            Set<Long> empIds = new HashSet<>();
            double totalHours = 0;
            for (CheckinRecord r : entry.getValue()) {
                if (r.getSignInTime() != null && r.getSignOutTime() != null) {
                    long ms = java.time.Duration.between(r.getSignInTime(), r.getSignOutTime()).toMillis();
                    totalHours += ms / (1000.0 * 60 * 60);
                    empIds.add(r.getEmpId());
                }
            }
            Map<String, Object> item = new HashMap<>();
            item.put("dept", dept);
            item.put("empCount", empIds.size());
            item.put("totalHours", Math.round(totalHours * 100) / 100.0);
            item.put("avgHours", empIds.isEmpty() ? 0 : Math.round(totalHours / empIds.size() * 100) / 100.0);
            item.put("overtimeHours", Math.round(deptOvertime.getOrDefault(dept, 0.0) * 100) / 100.0);
            result.add(item);
            existing.add(dept);
        }
        for (Map.Entry<String, Double> en : deptOvertime.entrySet()) {
            if (existing.contains(en.getKey())) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("dept", en.getKey());
            item.put("empCount", 0);
            item.put("totalHours", 0.0);
            item.put("avgHours", 0.0);
            item.put("overtimeHours", Math.round(en.getValue() * 100) / 100.0);
            result.add(item);
        }
        result.sort((a, b) -> Double.compare((Double) b.get("totalHours"), (Double) a.get("totalHours")));
        return Result.ok(result);
    }

    private long countCheckin(LocalDateTime start, LocalDateTime end, Long venueId) {
        return checkinService.count(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .ge(CheckinRecord::getSignInTime, start)
                        .lt(CheckinRecord::getSignInTime, end)
                        .eq(venueId != null, CheckinRecord::getVenueId, venueId));
    }

    /** Excel 文件响应（与数据分析模块导出保持一致） */
    private static ResponseEntity<byte[]> file(byte[] bytes, String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encoded);
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
