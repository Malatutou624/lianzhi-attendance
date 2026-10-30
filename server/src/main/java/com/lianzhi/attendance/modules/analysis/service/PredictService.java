package com.lianzhi.attendance.modules.analysis.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lianzhi.attendance.modules.analysis.util.ExcelUtil;
import com.lianzhi.attendance.modules.approval.entity.ApprovalRecord;
import com.lianzhi.attendance.modules.approval.service.ApprovalService;
import com.lianzhi.attendance.modules.checkin.entity.CheckinRecord;
import com.lianzhi.attendance.modules.checkin.service.CheckinService;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * 子功能3：考勤缺勤趋势预测（课程项目演示用轻量模型）
 *
 * 模型说明：
 * - 历史序列：过去 28 天（仅工作日，周一~周五）逐日统计
 *     absent   缺勤人数 = 在职员工数 − 当日实际打卡人数
 *     leave    请假人次 = 当日处于已通过请假单据覆盖期内的人数
 *     overtime 加班时长(h) = 当日处于已通过加班单据覆盖期内的时长合计
 * - 预测算法：按「星期几」取近 4 周同星期的历史均值（星期规律性最强的轻量方案），
 *   对未来 1~30 天的工作日逐日给出预测值，保留 1 位小数
 * - 只依赖本地计算，无需训练环境和第三方依赖，保证可运行
 */
@Service
public class PredictService {

    private static final int HISTORY_DAYS = 28;

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private EmployeeService employeeService;

    /**
     * 历史 + 预测双序列
     * @param dept    部门，空=全部
     * @param venueId 场所，空=全部
     * @param type absent缺勤 / leave请假 / overtime加班
     * @param days 预测未来天数 1~30
     */
    public Map<String, Object> predict(String dept, Long venueId, String type, int days) {
        if (days < 1) days = 1;
        if (days > 30) days = 30;
        if (type == null || type.isEmpty()) type = "absent";

        LocalDate today = LocalDate.now();
        LocalDate histStart = today.minusDays(HISTORY_DAYS - 1L);
        LocalDate futureEnd = today.plusDays(60); // 预测最多30个工作日，覆盖范围放宽到60天

        // 历史区间 [histStart 00:00, tomorrow 00:00)
        List<CheckinRecord> records = checkinService.list(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .ge(CheckinRecord::getSignInTime, histStart.atStartOfDay())
                        .lt(CheckinRecord::getSignInTime, today.plusDays(1).atStartOfDay()));
        // 审批单查询范围覆盖整个预测区间：已批准的未来请假/加班单是确定性事件，要体现在预测曲线上
        List<ApprovalRecord> approvals = approvalService.list(
                Wrappers.<ApprovalRecord>lambdaQuery()
                        .eq(ApprovalRecord::getStatus, "approved")
                        .ge(ApprovalRecord::getEndTime, histStart.atStartOfDay())
                        .le(ApprovalRecord::getStartTime, futureEnd.atStartOfDay()));

        // 部门+场所过滤：员工范围（缺勤人数的分母） + 记录归属（通过 empId）
        Map<Long, Employee> empMap = new HashMap<>();
        int activeTotal = 0;
        for (Employee e : employeeService.list()) {
            boolean deptMatch = dept == null || dept.isEmpty() || dept.equals(e.getDept());
            boolean venueMatch = venueId == null || venueId.equals(e.getVenueId());
            if (deptMatch && venueMatch && e.getStatus() != null && e.getStatus() == 1) {
                empMap.put(e.getId(), e);
                activeTotal++;
            }
        }

        // 逐工作日计算历史值
        List<Map<String, Object>> history = new ArrayList<>();
        Map<DayOfWeek, List<Double>> byDow = new EnumMap<>(DayOfWeek.class);
        for (int i = 0; i < HISTORY_DAYS; i++) {
            LocalDate d = histStart.plusDays(i);
            if (isWeekend(d)) continue; // 只看工作日，剔除周末噪声
            double value = dayValue(d, type, records, approvals, empMap, activeTotal);
            Map<String, Object> p = new HashMap<>();
            p.put("date", d.toString());
            p.put("value", value);
            history.add(p);
            byDow.computeIfAbsent(d.getDayOfWeek(), k -> new ArrayList<>()).add(value);
        }

        // 预测：未来工作日
        // - 请假/加班：若当天已有已批准单据覆盖（确定性事件），直接取单据实际值；否则用同星期近4周均值
        // - 缺勤：未来打卡不可预知，用同星期近4周均值
        List<Map<String, Object>> predictList = new ArrayList<>();
        int produced = 0;
        for (int i = 1; produced < days && i <= 60; i++) {
            LocalDate d = today.plusDays(i);
            if (isWeekend(d)) continue;
            List<Double> sameDow = byDow.getOrDefault(d.getDayOfWeek(), Collections.emptyList());
            double avg = sameDow.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double value = avg;
            if ("leave".equals(type) || "overtime".equals(type)) {
                double docValue = dayValue(d, type, records, approvals, empMap, activeTotal);
                if (docValue > 0) value = docValue; // 已批准单据优先，保证未来请假/加班能体现在曲线上
            }
            Map<String, Object> p = new HashMap<>();
            p.put("date", d.toString());
            p.put("value", Math.round(value * 100) / 100.0); // 2位小数，分钟级加班也能体现
            predictList.add(p);
            produced++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("type", type);
        result.put("name", switch (type) {
            case "leave" -> "请假人次";
            case "overtime" -> "加班时长(h)";
            default -> "缺勤人数";
        });
        result.put("history", history);
        result.put("predict", predictList);
        return result;
    }

    /** 导出Excel：历史值 + 预测值 */
    public byte[] export(String dept, Long venueId, String type, int days) {
        Map<String, Object> data = predict(dept, venueId, type, days);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) data.get("history");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> predict = (List<Map<String, Object>>) data.get("predict");

        String[] headers = {"日期", data.get("name").toString(), "数据类型"};
        List<Object[]> rows = new ArrayList<>();
        for (Map<String, Object> p : history) {
            rows.add(new Object[]{p.get("date"), p.get("value"), "历史真实值"});
        }
        for (Map<String, Object> p : predict) {
            rows.add(new Object[]{p.get("date"), p.get("value"), "预测值"});
        }
        return ExcelUtil.build("考勤趋势预测", headers, rows);
    }

    /** 某一天某个指标的值 */
    private double dayValue(LocalDate day, String type, List<CheckinRecord> records,
                            List<ApprovalRecord> approvals, Map<Long, Employee> emps, int activeTotal) {
        switch (type) {
            case "leave": {
                int count = 0;
                for (ApprovalRecord a : approvals) {
                    if (!isLeaveType(a.getType())) continue;
                    if (!emps.containsKey(a.getEmpId())) continue;
                    // 当天处于单据 [start, end] 覆盖期内 → 记 1 人次
                    if (!a.getStartTime().toLocalDate().isAfter(day)
                            && !a.getEndTime().toLocalDate().isBefore(day)) count++;
                }
                return count;
            }
            case "overtime": {
                double hours = 0;
                for (ApprovalRecord a : approvals) {
                    if (!"overtime".equals(a.getType())) continue;
                    if (!emps.containsKey(a.getEmpId())) continue;
                    if (!a.getStartTime().toLocalDate().isAfter(day)
                            && !a.getEndTime().toLocalDate().isBefore(day)) {
                        hours += durationOf(a);
                    }
                }
                return Math.round(hours * 100) / 100.0; // 保留2位小数，分钟级加班也能体现
            }
            default: { // absent 缺勤人数 = 在职人数 − 当日打卡人数
                Set<Long> checked = new HashSet<>();
                for (CheckinRecord r : records) {
                    if (r.getSignInTime() != null
                            && r.getSignInTime().toLocalDate().equals(day)
                            && emps.containsKey(r.getEmpId())) {
                        checked.add(r.getEmpId());
                    }
                }
                return Math.max(activeTotal - checked.size(), 0);
            }
        }
    }

    /** 单据时长（小时）：duration_hours 优先，为空或为0时按起止时间兜底计算 */
    public static double durationOf(ApprovalRecord a) {
        if (a.getDurationHours() != null && a.getDurationHours().doubleValue() > 0) {
            return a.getDurationHours().doubleValue();
        }
        if (a.getStartTime() != null && a.getEndTime() != null) {
            return java.time.Duration.between(a.getStartTime(), a.getEndTime()).toMinutes() / 60.0;
        }
        return 0;
    }

    private static boolean isLeaveType(String t) {
        return "leave".equals(t) || "sick".equals(t) || "annual".equals(t);
    }

    private static boolean isWeekend(LocalDate d) {
        DayOfWeek dow = d.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }
}
