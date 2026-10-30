package com.lianzhi.attendance.modules.stats.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lianzhi.attendance.modules.analysis.service.AttendanceStatusService;
import com.lianzhi.attendance.modules.checkin.entity.CheckinRecord;
import com.lianzhi.attendance.modules.checkin.service.CheckinService;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.service.EmployeeService;
import com.lianzhi.attendance.modules.stats.entity.DailyStats;
import com.lianzhi.attendance.modules.stats.mapper.DailyStatsMapper;
import com.lianzhi.attendance.modules.venue.entity.Venue;
import com.lianzhi.attendance.modules.venue.service.VenueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 每日考勤自动同步任务（数据分析模块"自动刷新"能力）
 * 每天凌晨 00:30 自动统计前一天的打卡数据，写入 daily_stats 表
 * 幂等设计：同一 (日期, 场所) 已有统计则覆盖更新，任务重复执行也不会产生脏数据
 */
@Component
public class DailyStatsTask {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private VenueService venueService;

    @Autowired
    private DailyStatsMapper dailyStatsMapper;

    @Autowired
    private AttendanceStatusService statusService;

    /** 秒 分 时 日 月 周：每天 00:30 执行一次 */
    @Scheduled(cron = "0 30 0 * * ?")
    public void syncYesterday() {
        sync(LocalDate.now().minusDays(1));
    }

    /** 统计指定日期的各场所考勤数据（也供手动补数调用） */
    public void sync(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();

        // 当天所有打卡记录
        List<CheckinRecord> records = checkinService.list(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .ge(CheckinRecord::getSignInTime, start)
                        .lt(CheckinRecord::getSignInTime, end));

        // 在职员工按场所分组（应到人数）
        List<Employee> active = new ArrayList<>();
        for (Employee e : employeeService.list()) {
            if (e.getStatus() != null && e.getStatus() == 1 && e.getVenueId() != null) active.add(e);
        }

        // 逐场所统计（统一走 AttendanceStatusService）
        for (Venue v : venueService.list()) {
            List<Employee> venueEmps = new ArrayList<>();
            for (Employee e : active) {
                if (v.getId().equals(e.getVenueId())) venueEmps.add(e);
            }
            int empTotal = venueEmps.size();
            Map<String, Long> dist = statusService.countByDay(venueEmps, date);
            int absentTotal = dist.getOrDefault(AttendanceStatusService.ABSENT, 0L).intValue();
            int checkinTotal = empTotal - absentTotal - dist.getOrDefault(AttendanceStatusService.LEAVE, 0L).intValue();
            int lateTotal = dist.getOrDefault(AttendanceStatusService.LATE, 0L).intValue()
                    + dist.getOrDefault(AttendanceStatusService.LATE_EARLY, 0L).intValue();

            // 幂等 upsert：按 (stat_date, venue_id) 查旧记录，有则更新无则插入
            DailyStats old = dailyStatsMapper.selectOne(
                    Wrappers.<DailyStats>lambdaQuery()
                            .eq(DailyStats::getStatDate, date)
                            .eq(DailyStats::getVenueId, v.getId()));
            if (old == null) {
                DailyStats s = new DailyStats();
                s.setStatDate(date);
                s.setVenueId(v.getId());
                s.setEmpTotal(empTotal);
                s.setCheckinTotal(checkinTotal);
                s.setLateTotal(lateTotal);
                s.setAbsentTotal(absentTotal);
                dailyStatsMapper.insert(s);
            } else {
                old.setEmpTotal(empTotal);
                old.setCheckinTotal(checkinTotal);
                old.setLateTotal(lateTotal);
                old.setAbsentTotal(absentTotal);
                dailyStatsMapper.updateById(old);
            }
        }
    }
}
