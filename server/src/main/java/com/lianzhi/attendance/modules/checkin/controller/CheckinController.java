package com.lianzhi.attendance.modules.checkin.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.checkin.entity.CheckinRecord;
import com.lianzhi.attendance.modules.checkin.service.CheckinService;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/checkin")
public class CheckinController {

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private EmployeeService employeeService;

    @GetMapping("/page")
    public Result<Page<CheckinRecord>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {

        // 按部门筛选：先查出该部门的所有员工ID
        List<Long> empIds = null;
        if (dept != null && !dept.isEmpty()) {
            empIds = employeeService.list(
                    Wrappers.<Employee>lambdaQuery().eq(Employee::getDept, dept))
                    .stream().map(Employee::getId).collect(Collectors.toList());
            if (empIds.isEmpty()) {
                // 该部门没有员工，直接返回空页
                Page<CheckinRecord> empty = new Page<>(current, size);
                empty.setRecords(List.of());
                empty.setTotal(0);
                return Result.ok(empty);
            }
        }

        Page<CheckinRecord> page = checkinService.page(new Page<>(current, size),
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(empId != null, CheckinRecord::getEmpId, empId)
                        .in(empIds != null && !empIds.isEmpty(), CheckinRecord::getEmpId, empIds)
                        .eq(venueId != null, CheckinRecord::getVenueId, venueId)
                        .ge(date != null, CheckinRecord::getSignInTime, date != null ? date.atStartOfDay() : null)
                        .lt(date != null, CheckinRecord::getSignInTime, date != null ? date.plusDays(1).atStartOfDay() : null)
                        .orderByDesc(CheckinRecord::getSignInTime));
        checkinService.fillNames(page);

        // 按员工状态筛选（内存中过滤，因为签到/签退是动态判断的）
        if (status != null && !status.isEmpty()) {
            List<CheckinRecord> filtered = page.getRecords().stream()
                    .filter(r -> {
                        boolean isSignIn = r.getSignInTime() != null;
                        boolean isSignOut = r.getSignOutTime() != null;
                        switch (status) {
                            case "sign_in_normal": return isSignIn && "normal".equals(r.getStatus());
                            case "sign_in_late": return isSignIn && "late".equals(r.getStatus());
                            case "sign_out_normal": return isSignOut && "normal".equals(r.getStatus());
                            case "sign_out_early": return isSignOut && "early".equals(r.getStatus());
                            default: return true;
                        }
                    })
                    .collect(Collectors.toList());
            page.setRecords(filtered);
            page.setTotal(filtered.size());
        }
        return Result.ok(page);
    }
}
