package com.lianzhi.attendance.modules.stats.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.checkin.entity.CheckinRecord;
import com.lianzhi.attendance.modules.checkin.service.CheckinService;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.service.EmployeeService;
import com.lianzhi.attendance.modules.venue.entity.Venue;
import com.lianzhi.attendance.modules.venue.service.VenueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据看板统计接口
 * GET /stats/today  → 今日概览：员工总数、今日打卡数、迟到数、未打卡数
 * 更复杂的趋势图、热力图接口第2周在这里追加
 */
@RestController
@RequestMapping("/stats")
public class StatsController {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private VenueService venueService;

    @GetMapping("/today")
    public Result<Map<String, Object>> today() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

        long empTotal = employeeService.count(
                Wrappers.<Employee>lambdaQuery().eq(Employee::getStatus, 1));

        // 今日打卡记录（每人每天最多一条）
        List<CheckinRecord> todayRecords = checkinService.list(
                Wrappers.<CheckinRecord>lambdaQuery().ge(CheckinRecord::getSignInTime, startOfToday));

        // 打卡人数
        long checkedCount = todayRecords.size();
        // 迟到人数
        long lateCount = todayRecords.stream().filter(r -> "late".equals(r.getStatus())).count();

        Map<String, Object> data = new HashMap<>();
        data.put("empTotal", empTotal);
        data.put("todayCheckin", checkedCount);
        data.put("todayLate", lateCount);
        data.put("notCheckin", Math.max(empTotal - checkedCount, 0));
        return Result.ok(data);
    }

    /**
     * 获取各类人员列表
     * type: active(在职人员) / checked(今日打卡) / late(今日迟到) / unchecked(未打卡)
     */
    @GetMapping("/people")
    public Result<List<Map<String, Object>>> people(@RequestParam String type) {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

        // 查所有在职员工
        List<Employee> allActive = employeeService.list(
                Wrappers.<Employee>lambdaQuery().eq(Employee::getStatus, 1));

        // 查今日打卡记录（每人每天一条）
        List<CheckinRecord> todayRecords = checkinService.list(
                Wrappers.<CheckinRecord>lambdaQuery().ge(CheckinRecord::getSignInTime, startOfToday));
        Map<Long, CheckinRecord> recordMap = todayRecords.stream()
                .collect(Collectors.toMap(CheckinRecord::getEmpId, r -> r));

        // 查场所名称映射
        Map<Long, String> venueMap = venueService.list().stream()
                .collect(Collectors.toMap(Venue::getId, Venue::getName));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Employee emp : allActive) {
            CheckinRecord record = recordMap.get(emp.getId());
            boolean checked = record != null;
            boolean late = checked && "late".equals(record.getStatus());

            boolean match = switch (type) {
                case "active" -> true;
                case "checked" -> checked;
                case "late" -> late;
                case "unchecked" -> !checked;
                default -> false;
            };
            if (!match) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("id", emp.getId());
            item.put("empNo", emp.getEmpNo());
            item.put("name", emp.getName());
            item.put("dept", emp.getDept());
            item.put("position", emp.getPosition());
            item.put("venueName", venueMap.get(emp.getVenueId()));
            item.put("checked", checked);
            if (record != null) {
                item.put("checkinTime", record.getSignInTime().toString().replace("T", " ").substring(0, 19));
                item.put("status", record.getStatus());
            }
            result.add(item);
        }
        return Result.ok(result);
    }

    /**
     * 按部门统计工作时长报告
     * GET /stats/workHours?month=2026-10
     */
    @GetMapping("/workHours")
    public Result<List<Map<String, Object>>> workHours(@RequestParam(required = false) String month) {
        // 默认查当月
        YearMonth yearMonth;
        if (month != null && !month.isEmpty()) {
            yearMonth = YearMonth.parse(month);
        } else {
            yearMonth = YearMonth.now();
        }

        LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime end = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        // 查该月所有打卡记录
        List<CheckinRecord> records = checkinService.list(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .ge(CheckinRecord::getSignInTime, start)
                        .lt(CheckinRecord::getSignInTime, end)
                        .isNotNull(CheckinRecord::getSignOutTime));

        // 查所有员工
        List<Employee> allEmps = employeeService.list();
        Map<Long, Employee> empMap = allEmps.stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        // 按部门分组统计
        Map<String, List<CheckinRecord>> deptMap = new HashMap<>();
        for (CheckinRecord r : records) {
            Employee emp = empMap.get(r.getEmpId());
            if (emp == null || emp.getDept() == null || emp.getDept().isEmpty()) {
                continue;
            }
            deptMap.computeIfAbsent(emp.getDept(), k -> new ArrayList<>()).add(r);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<CheckinRecord>> entry : deptMap.entrySet()) {
            String dept = entry.getKey();
            List<CheckinRecord> deptRecords = entry.getValue();

            // 计算该部门总工时和平均工时
            double totalHours = 0;
            int validDays = 0;
            Set<Long> empIds = new HashSet<>();
            for (CheckinRecord r : deptRecords) {
                if (r.getSignInTime() != null && r.getSignOutTime() != null) {
                    long ms = java.time.Duration.between(r.getSignInTime(), r.getSignOutTime()).toMillis();
                    totalHours += ms / (1000.0 * 60 * 60);
                    validDays++;
                    empIds.add(r.getEmpId());
                }
            }

            Map<String, Object> item = new HashMap<>();
            item.put("dept", dept);
            item.put("empCount", empIds.size());           // 部门打卡人数
            item.put("totalHours", Math.round(totalHours * 100) / 100.0); // 总工时（小时）
            item.put("avgHours", empIds.isEmpty() ? 0 : Math.round(totalHours / empIds.size() * 100) / 100.0); // 人均工时
            result.add(item);
        }

        // 按总工时降序排序
        result.sort((a, b) -> Double.compare((Double) b.get("totalHours"), (Double) a.get("totalHours")));
        return Result.ok(result);
    }
}
