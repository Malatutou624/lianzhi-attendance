package com.lianzhi.attendance.modules.stats.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lianzhi.attendance.modules.checkin.entity.CheckinRecord;
import com.lianzhi.attendance.modules.checkin.service.CheckinService;
import com.lianzhi.attendance.modules.common.entity.SystemNotice;
import com.lianzhi.attendance.modules.common.mapper.SystemNoticeMapper;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.service.EmployeeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * 补卡提醒任务
 * 每天 19:00 扫描当日未上班打卡 / 未下班打卡的员工，推送补卡提醒通知
 *
 * cron: "0 0 19 * * ?" —— 每天 19:00 执行
 */
@Slf4j
@Component
public class ReminderTask {

    @Autowired private EmployeeService employeeService;
    @Autowired private CheckinService checkinService;
    @Autowired private SystemNoticeMapper systemNoticeMapper;

    /** 每天 19:00 推送补卡提醒 */
    @Scheduled(cron = "0 0 19 * * ?")
    public void pushMakeupReminder() {
        // 如果今天是周末，跳过
        DayOfWeek dow = LocalDate.now().getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            log.info("[补卡提醒] 今天是周末，跳过");
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.atTime(LocalTime.MAX);

        // 在职员工
        List<Employee> active = employeeService.list(
                Wrappers.<Employee>lambdaQuery().eq(Employee::getStatus, 1));

        // 今日所有打卡记录
        List<CheckinRecord> todayRecords = checkinService.list(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .ge(CheckinRecord::getSignInTime, dayStart)
                        .lt(CheckinRecord::getSignInTime, dayEnd));
        Set<Long> checkedEmpIds = new HashSet<>();
        for (CheckinRecord r : todayRecords) {
            if (r.getSignInTime() != null) checkedEmpIds.add(r.getEmpId());
        }

        // 未打卡的员工 → 推送
        int count = 0;
        for (Employee emp : active) {
            if (!checkedEmpIds.contains(emp.getId())) {
                try {
                    SystemNotice notice = new SystemNotice();
                    notice.setUserId(emp.getId());
                    notice.setUserType("h5");
                    notice.setType("MAKEUP_REMIND");
                    notice.setTitle("⏰ 今日未打卡，记得补卡！");
                    notice.setContent(String.format(
                            "%s 您好，今天（%s）系统未检测到您的打卡记录。如因工作忙忘了打卡，请在 3 个工作日内到 OA 审批中心提交《补卡申请单》。每月补卡超过 4 次且无客观证明材料将按事假处理。",
                            emp.getName(), today.toString()));
                    notice.setIsRead(false);
                    systemNoticeMapper.insert(notice);
                    count++;
                } catch (Exception e) {
                    log.warn("[补卡提醒] 员工 {} 推送失败: {}", emp.getName(), e.getMessage());
                }
            }
        }

        log.info("[补卡提醒] 今日 {}，共 {} 名未打卡员工，已推送 {} 条提醒",
                today, active.size() - checkedEmpIds.size(), count);
    }
}
