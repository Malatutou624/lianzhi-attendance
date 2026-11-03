package com.lianzhi.attendance.modules.stats.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lianzhi.attendance.modules.approval.entity.ApprovalRecord;
import com.lianzhi.attendance.modules.approval.service.ApprovalService;
import com.lianzhi.attendance.modules.checkin.entity.CheckinRecord;
import com.lianzhi.attendance.modules.checkin.service.CheckinService;
import com.lianzhi.attendance.modules.common.entity.SystemNotice;
import com.lianzhi.attendance.modules.common.mapper.SystemNoticeMapper;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.service.EmployeeService;
import com.lianzhi.attendance.modules.stats.entity.MonthlyReport;
import com.lianzhi.attendance.modules.stats.mapper.MonthlyReportMapper;
import com.lianzhi.attendance.modules.venue.entity.Venue;
import com.lianzhi.attendance.modules.venue.service.VenueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/**
 * 考勤月报自动生成任务
 * 每月 1 号 凌晨 00:05 自动生成上月全员考勤月报
 * 同时给每个员工推一条系统通知
 *
 * cron: "0 5 0 1 * ?" —— 每月 1 号 00:05 执行
 */
@Slf4j
@Component
public class MonthlyReportTask {

    @Autowired private EmployeeService employeeService;
    @Autowired private CheckinService checkinService;
    @Autowired private ApprovalService approvalService;
    @Autowired private VenueService venueService;
    @Autowired private MonthlyReportMapper monthlyReportMapper;
    @Autowired private SystemNoticeMapper systemNoticeMapper;

    /** 每月 1 号 00:05 生成上月月报 */
    @Scheduled(cron = "0 5 0 1 * ?")
    public void generateLastMonth() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        generate(lastMonth);
    }

    /** 生成指定月份的月报（也供手动补数调用） */
    public void generate(YearMonth ym) {
        String monthStr = ym.toString();  // e.g. 2026-10
        LocalDateTime monthStart = ym.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = ym.plusMonths(1).atDay(1).atStartOfDay();

        log.info("[月报] 开始生成 {} 月度考勤月报", monthStr);

        List<Employee> activeEmps = employeeService.list(
                Wrappers.<Employee>lambdaQuery().eq(Employee::getStatus, 1));

        int okCount = 0;
        for (Employee emp : activeEmps) {
            try {
                // 本月该员工所有打卡记录
                List<CheckinRecord> records = checkinService.list(
                        Wrappers.<CheckinRecord>lambdaQuery()
                                .eq(CheckinRecord::getEmpId, emp.getId())
                                .ge(CheckinRecord::getSignInTime, monthStart)
                                .lt(CheckinRecord::getSignInTime, monthEnd));

                int lateCount = 0, earlyCount = 0, absentCount = 0;
                double totalMinutes = 0;
                Set<LocalDate> workedDays = new HashSet<>();

                for (CheckinRecord r : records) {
                    if ("late".equals(r.getStatus()) || "lateEarly".equals(r.getStatus())) lateCount++;
                    if ("early".equals(r.getStatus()) || "lateEarly".equals(r.getStatus())) earlyCount++;
                    if (r.getSignInTime() != null) workedDays.add(r.getSignInTime().toLocalDate());
                    if (r.getSignInTime() != null && r.getSignOutTime() != null) {
                        totalMinutes += Duration.between(r.getSignInTime(), r.getSignOutTime()).toMinutes();
                    }
                }

                // 请假天数（审批记录）
                List<ApprovalRecord> leaves = approvalService.list(
                        Wrappers.<ApprovalRecord>lambdaQuery()
                                .eq(ApprovalRecord::getEmpId, emp.getId())
                                .in(ApprovalRecord::getStatus, "approved", "APPROVED", "通过")
                                .ge(ApprovalRecord::getStartTime, monthStart)
                                .lt(ApprovalRecord::getStartTime, monthEnd));
                double leaveDays = leaves.stream()
                        .mapToDouble(r -> r.getDurationHours() != null
                                ? r.getDurationHours().divide(BigDecimal.valueOf(8), 4, RoundingMode.HALF_UP).doubleValue()
                                : 0)
                        .sum();

                // 旷工天数：应出勤 - 实际出勤 - 请假
                int workDaysInMonth = countWorkdays(ym);
                int absent = Math.max(0, workDaysInMonth - workedDays.size() - (int) Math.round(leaveDays));

                Venue venue = emp.getVenueId() != null ? venueService.getById(emp.getVenueId()) : null;

                MonthlyReport report = new MonthlyReport();
                report.setReportMonth(monthStr);
                report.setEmpId(emp.getId());
                report.setEmpName(emp.getName());
                report.setDept(emp.getDept());
                report.setVenueId(emp.getVenueId());
                report.setVenueName(venue != null ? venue.getName() : null);
                report.setWorkDays(workedDays.size());
                report.setLateCount(lateCount);
                report.setEarlyCount(earlyCount);
                report.setAbsentCount(absent);
                report.setLeaveCount((int) Math.round(leaveDays));
                report.setOvertimeHours(BigDecimal.ZERO);  // 简化版，可后续扩展
                report.setTotalHours(BigDecimal.valueOf(totalMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));                // 幂等 upsert
                MonthlyReport old = monthlyReportMapper.selectOne(
                        Wrappers.<MonthlyReport>lambdaQuery()
                                .eq(MonthlyReport::getReportMonth, monthStr)
                                .eq(MonthlyReport::getEmpId, emp.getId()));
                if (old != null) {
                    report.setId(old.getId());
                    monthlyReportMapper.updateById(report);
                } else {
                    monthlyReportMapper.insert(report);
                }

                // 给员工推月报通知
                SystemNotice notice = new SystemNotice();
                notice.setUserId(emp.getId());
                notice.setUserType("h5");
                notice.setType("MONTHLY_REPORT");
                notice.setTitle("📊 " + monthStr + " 考勤月报已生成");
                notice.setContent(buildReportContent(emp.getName(), report));
                notice.setRelatedId(report.getId());
                notice.setIsRead(false);
                systemNoticeMapper.insert(notice);

                okCount++;
            } catch (Exception e) {
                log.warn("[月报] 员工 {} 生成失败: {}", emp.getName(), e.getMessage());
            }
        }

        log.info("[月报] {} 生成完成，共 {} 人成功 / {} 人在职", monthStr, okCount, activeEmps.size());
    }

    /** 计算指定月份的工作日天数（粗略：按总天数 * 5/7） */
    private int countWorkdays(YearMonth ym) {
        int total = ym.lengthOfMonth();
        // 精确计算：遍历每一天跳过周六周日
        int work = 0;
        for (int day = 1; day <= total; day++) {
            DayOfWeek dow = ym.atDay(day).getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) work++;
        }
        return work;
    }

    private String buildReportContent(String name, MonthlyReport r) {
        return String.format(
                "员工 %s，%s 考勤数据：\n出勤 %d 天 | 迟到 %d 次 | 早退 %d 次 | 请假 %d 天 | 旷工 %d 天 | 累计工时 %s h",
                name, r.getReportMonth(), r.getWorkDays(), r.getLateCount(),
                r.getEarlyCount(), r.getLeaveCount(), r.getAbsentCount(), r.getTotalHours());
    }
}
