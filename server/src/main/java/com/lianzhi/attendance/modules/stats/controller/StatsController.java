package com.lianzhi.attendance.modules.stats.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.checkin.entity.CheckinRecord;
import com.lianzhi.attendance.modules.checkin.service.CheckinService;
import com.lianzhi.attendance.modules.employee.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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

    @GetMapping("/today")
    public Result<Map<String, Object>> today() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

        long empTotal = employeeService.count(
                Wrappers.<com.lianzhi.attendance.modules.employee.entity.Employee>lambdaQuery()
                        .eq(com.lianzhi.attendance.modules.employee.entity.Employee::getStatus, 1));
        long todayCheckin = checkinService.count(
                Wrappers.<CheckinRecord>lambdaQuery().ge(CheckinRecord::getCheckinTime, startOfToday));
        long todayLate = checkinService.count(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .ge(CheckinRecord::getCheckinTime, startOfToday)
                        .eq(CheckinRecord::getStatus, "late"));

        Map<String, Object> data = new HashMap<>();
        data.put("empTotal", empTotal);
        data.put("todayCheckin", todayCheckin);
        data.put("todayLate", todayLate);
        data.put("notCheckin", Math.max(empTotal - todayCheckin, 0));
        return Result.ok(data);
    }
}
