package com.lianzhi.attendance.modules.checkin.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.checkin.entity.CheckinRecord;
import com.lianzhi.attendance.modules.checkin.service.CheckinService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 打卡记录查询接口（管理端）
 * GET /checkin/page?current=1&size=10&date=2026-10-24&empId=1&venueId=1
 *
 * 注意：员工打卡的提交接口在 H5 端（/h5/checkin），第2周开发
 */
@RestController
@RequestMapping("/checkin")
public class CheckinController {

    @Autowired
    private CheckinService checkinService;

    @GetMapping("/page")
    public Result<Page<CheckinRecord>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long empId,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        Page<CheckinRecord> page = checkinService.page(new Page<>(current, size),
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(empId != null, CheckinRecord::getEmpId, empId)
                        .eq(venueId != null, CheckinRecord::getVenueId, venueId)
                        .ge(date != null, CheckinRecord::getCheckinTime, date != null ? date.atStartOfDay() : null)
                        .lt(date != null, CheckinRecord::getCheckinTime, date != null ? date.plusDays(1).atStartOfDay() : null)
                        .orderByDesc(CheckinRecord::getCheckinTime));
        checkinService.fillNames(page);
        return Result.ok(page);
    }
}
