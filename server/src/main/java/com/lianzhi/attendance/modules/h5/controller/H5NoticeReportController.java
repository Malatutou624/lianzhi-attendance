package com.lianzhi.attendance.modules.h5.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.common.entity.SystemNotice;
import com.lianzhi.attendance.modules.common.mapper.SystemNoticeMapper;
import com.lianzhi.attendance.modules.h5.entity.H5Account;
import com.lianzhi.attendance.modules.h5.mapper.H5AccountMapper;
import com.lianzhi.attendance.modules.stats.entity.MonthlyReport;
import com.lianzhi.attendance.modules.stats.mapper.MonthlyReportMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * H5 员工端：通知中心 + 月报接口
 * GET  /h5/notice/list            —— 我的通知列表（补卡提醒、月报、系统公告）
 * POST /h5/notice/{id}/read       —— 标记通知已读
 * POST /h5/notice/read-all        —— 全部标记已读
 * GET  /h5/notice/unread          —— 未读通知数量（portal 小红点用）
 * GET  /h5/report/my?month=2026-10 —— 我的月报（指定月或最近一月）
 * GET  /h5/report/list            —— 我的月报历史（下拉选择月份用）
 */
@Slf4j
@RestController
@RequestMapping("/h5")
public class H5NoticeReportController {

    @Autowired private SystemNoticeMapper noticeMapper;
    @Autowired private MonthlyReportMapper reportMapper;
    @Autowired private H5AccountMapper accountMapper;

    private Long resolveEmpId(HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("h5AccountId");
        if (accountId == null) return null;
        H5Account acc = accountMapper.selectById(accountId);
        return acc == null ? null : acc.getEmpId();
    }

    // ========== 通知 ==========

    @GetMapping("/notice/list")
    public Result<List<SystemNotice>> list(HttpServletRequest req) {
        Long empId = resolveEmpId(req);
        if (empId == null) return Result.ok(List.of());
        List<SystemNotice> list = noticeMapper.selectList(
                Wrappers.<SystemNotice>lambdaQuery()
                        .eq(SystemNotice::getUserId, empId)
                        .orderByDesc(SystemNotice::getCreateTime)
                        .last("LIMIT 30"));
        return Result.ok(list);
    }

    @GetMapping("/notice/unread")
    public Result<Long> unread(HttpServletRequest req) {
        Long empId = resolveEmpId(req);
        if (empId == null) return Result.ok(0L);
        Long count = noticeMapper.selectCount(
                Wrappers.<SystemNotice>lambdaQuery()
                        .eq(SystemNotice::getUserId, empId)
                        .eq(SystemNotice::getIsRead, false));
        return Result.ok(count);
    }

    @PostMapping("/notice/{id}/read")
    public Result<Void> read(@PathVariable Long id, HttpServletRequest req) {
        Long empId = resolveEmpId(req);
        SystemNotice n = noticeMapper.selectById(id);
        if (n != null && empId != null && n.getUserId().equals(empId)) {
            n.setIsRead(true);
            noticeMapper.updateById(n);
        }
        return Result.ok();
    }

    @PostMapping("/notice/read-all")
    public Result<Void> readAll(HttpServletRequest req) {
        Long empId = resolveEmpId(req);
        if (empId == null) return Result.ok();
        noticeMapper.update(null,
                Wrappers.<SystemNotice>lambdaUpdate()
                        .eq(SystemNotice::getUserId, empId)
                        .eq(SystemNotice::getIsRead, false)
                        .set(SystemNotice::getIsRead, true));
        return Result.ok();
    }

    // ========== 月报 ==========

    @GetMapping("/report/my")
    public Result<MonthlyReport> myReport(
            @RequestParam(required = false) String month,
            HttpServletRequest req) {
        Long empId = resolveEmpId(req);
        if (empId == null) return Result.ok(null);
        MonthlyReport report;
        if (month != null && !month.isBlank()) {
            report = reportMapper.selectOne(
                    Wrappers.<MonthlyReport>lambdaQuery()
                            .eq(MonthlyReport::getEmpId, empId)
                            .eq(MonthlyReport::getReportMonth, month));
        } else {
            report = reportMapper.selectOne(
                    Wrappers.<MonthlyReport>lambdaQuery()
                            .eq(MonthlyReport::getEmpId, empId)
                            .orderByDesc(MonthlyReport::getReportMonth)
                            .last("LIMIT 1"));
        }
        return Result.ok(report);
    }

    @GetMapping("/report/list")
    public Result<List<MonthlyReport>> reportList(HttpServletRequest req) {
        Long empId = resolveEmpId(req);
        if (empId == null) return Result.ok(List.of());
        return Result.ok(reportMapper.selectList(
                Wrappers.<MonthlyReport>lambdaQuery()
                        .eq(MonthlyReport::getEmpId, empId)
                        .orderByDesc(MonthlyReport::getReportMonth)
                        .last("LIMIT 12")));
    }
}
