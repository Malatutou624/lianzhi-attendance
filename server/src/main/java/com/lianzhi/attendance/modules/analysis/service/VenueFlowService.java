package com.lianzhi.attendance.modules.analysis.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lianzhi.attendance.modules.analysis.util.ExcelUtil;
import com.lianzhi.attendance.modules.checkin.entity.CheckinRecord;
import com.lianzhi.attendance.modules.checkin.service.CheckinService;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.service.EmployeeService;
import com.lianzhi.attendance.modules.venue.entity.Venue;
import com.lianzhi.attendance.modules.venue.service.VenueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 子功能1：办公场所人流分析
 *
 * 统计口径：
 * - 每条打卡记录代表一名员工在 [签到时间, 签退时间] 内身处该场所
 * - 签退为空（缺卡）时视为当天营业时段结束（22:00）离开
 * - 统计时段窗口固定为 06:00 ~ 22:00（办公常见时段），按小时切片
 * - 数据实时从 checkin_record 原始表计算，打卡数据一变，报表自动更新
 */
@Service
public class VenueFlowService {

    /** 统计窗口：6点~22点 */
    private static final int WINDOW_START = 6;
    private static final int WINDOW_END = 22;
    /** 时段 key：完整日期+小时（分钟恒为00），跨年不会歧义，明细穿透可直接复用 */
    private static final DateTimeFormatter SLOT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private VenueService venueService;

    /**
     * 人流时序统计
     * @param venueId  场所ID，空=全部场所
     * @param start    开始日期（含）
     * @param end      结束日期（含）
     * @param dayType  all全部 / workday工作日 / weekend休息日
     * @return venues: [{venueId, venueName, points:[{time,count}], peak, peakTime, valley, avg}]
     */
    public Map<String, Object> flow(Long venueId, LocalDate start, LocalDate end, String dayType) {
        List<CheckinRecord> records = queryRecords(venueId, start, end, dayType);

        // 场所名称映射
        Map<Long, String> venueNames = new HashMap<>();
        for (Venue v : venueService.list()) venueNames.put(v.getId(), v.getName());

        // 分组：场所 → (时段 → 在场员工集合)。用 Set 去重（一人一天只有一条记录，但保险起见）
        Map<Long, Map<String, Set<Long>>> slotMap = new LinkedHashMap<>(); // venueId -> slotKey -> empIds
        Map<String, LocalDateTime> slotTimes = new TreeMap<>();            // slotKey -> 该时段起点时间（排序用）

        for (CheckinRecord r : records) {
            LocalDateTime in = r.getSignInTime();
            LocalDateTime out = r.getSignOutTime();
            LocalDate day = in.toLocalDate();
            Long vid = r.getVenueId() == null ? 0L : r.getVenueId();

            for (int h = WINDOW_START; h < WINDOW_END; h++) {
                LocalDateTime slotStart = day.atTime(h, 0);
                LocalDateTime slotEnd = slotStart.plusHours(1);
                // 员工在该小时内在场：签到早于时段结束 且 签退晚于时段开始（签退空=当天22点才走）
                boolean present = in.isBefore(slotEnd)
                        && (out != null ? out.isAfter(slotStart) : day.atTime(WINDOW_END, 0).isAfter(slotStart));
                if (!present) continue;
                String key = slotStart.format(SLOT_FMT);
                slotMap.computeIfAbsent(vid, k -> new LinkedHashMap<>())
                        .computeIfAbsent(key, k -> new HashSet<>()).add(r.getEmpId());
                slotTimes.putIfAbsent(key, slotStart);
            }
        }

        // 组装结果：每个场所一条曲线
        List<Map<String, Object>> venues = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Set<Long>>> entry : slotMap.entrySet()) {
            Map<String, Set<Long>> slots = entry.getValue();
            List<Map<String, Object>> points = new ArrayList<>();
            int peak = 0, valley = Integer.MAX_VALUE, total = 0, slotCount = 0;
            String peakTime = "-", valleyTime = "-";

            for (Map.Entry<String, LocalDateTime> t : slotTimes.entrySet()) {
                int count = slots.getOrDefault(t.getKey(), Collections.emptySet()).size();
                Map<String, Object> p = new HashMap<>();
                p.put("time", t.getKey());
                p.put("count", count);
                points.add(p);
                total += count;
                slotCount++;
                if (count > peak) { peak = count; peakTime = t.getKey(); }
                // 谷值只统计有人在场的小时（凌晨0人的低谷没有分析意义）
                if (count > 0 && count < valley) { valley = count; valleyTime = t.getKey(); }
            }
            if (valley == Integer.MAX_VALUE) { valley = 0; }

            Map<String, Object> venue = new HashMap<>();
            venue.put("venueId", entry.getKey());
            venue.put("venueName", venueNames.getOrDefault(entry.getKey(), "未分配场所"));
            venue.put("points", points);
            venue.put("peak", peak);
            venue.put("peakTime", peakTime);
            venue.put("valley", valley);
            venue.put("valleyTime", valleyTime);
            venue.put("avg", slotCount == 0 ? 0 : Math.round(total * 10.0 / slotCount) / 10.0);
            venues.add(venue);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("venues", venues);
        return result;
    }

    /**
     * 明细穿透：某个场所某个小时在场的人员名单
     * @param slot 时段，格式 yyyy-MM-dd HH时
     */
    public List<Map<String, Object>> detail(Long venueId, String slot) {
        LocalDateTime slotStart = LocalDateTime.parse(slot, SLOT_FMT);
        LocalDateTime slotEnd = slotStart.plusHours(1);

        List<CheckinRecord> records = checkinService.list(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(venueId != null, CheckinRecord::getVenueId, venueId)
                        .lt(CheckinRecord::getSignInTime, slotEnd)
                        .and(w -> w.ge(CheckinRecord::getSignOutTime, slotStart)
                                .or().isNull(CheckinRecord::getSignOutTime)));

        Map<Long, Employee> empMap = new HashMap<>();
        for (Employee e : employeeService.list()) empMap.put(e.getId(), e);
        Map<Long, String> venueNames = new HashMap<>();
        for (Venue v : venueService.list()) venueNames.put(v.getId(), v.getName());

        List<Map<String, Object>> list = new ArrayList<>();
        for (CheckinRecord r : records) {
            Employee e = empMap.get(r.getEmpId());
            if (e == null) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("empNo", e.getEmpNo());
            item.put("name", e.getName());
            item.put("dept", e.getDept());
            item.put("venueName", venueNames.getOrDefault(r.getVenueId(), "-"));
            item.put("signInTime", fmt(r.getSignInTime()));
            item.put("signOutTime", r.getSignOutTime() == null ? "未签退" : fmt(r.getSignOutTime()));
            list.add(item);
        }
        list.sort(Comparator.comparing(a -> a.get("signInTime").toString()));
        return list;
    }

    /** 导出Excel：人流明细 + 指标汇总 */
    public byte[] export(Long venueId, LocalDate start, LocalDate end, String dayType) {
        Map<String, Object> flow = flow(venueId, start, end, dayType);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> venues = (List<Map<String, Object>>) flow.get("venues");

        String[] headers = {"场所", "时段", "在场人数", "峰值人数", "峰值时段", "谷值人数", "平均在场人数"};
        List<Object[]> rows = new ArrayList<>();
        for (Map<String, Object> v : venues) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> points = (List<Map<String, Object>>) v.get("points");
            for (Map<String, Object> p : points) {
                rows.add(new Object[]{v.get("venueName"), p.get("time"), p.get("count"),
                        null, null, null, null});
            }
            // 每个场所后面跟一行指标汇总
            rows.add(new Object[]{v.get("venueName") + " — 指标汇总", null, null,
                    v.get("peak"), v.get("peakTime"), v.get("valley"), v.get("avg")});
        }
        return ExcelUtil.build("场所人流分析", headers, rows);
    }

    /** 按条件查询原始打卡记录（含日期类型过滤） */
    private List<CheckinRecord> queryRecords(Long venueId, LocalDate start, LocalDate end, String dayType) {
        List<CheckinRecord> records = checkinService.list(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(venueId != null, CheckinRecord::getVenueId, venueId)
                        .ge(CheckinRecord::getSignInTime, start.atStartOfDay())
                        .lt(CheckinRecord::getSignInTime, end.plusDays(1).atStartOfDay())
                        .isNotNull(CheckinRecord::getSignInTime));

        if (dayType == null || "all".equals(dayType)) return records;
        List<CheckinRecord> filtered = new ArrayList<>();
        for (CheckinRecord r : records) {
            DayOfWeek dow = r.getSignInTime().getDayOfWeek();
            boolean isWorkday = dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
            if ("workday".equals(dayType) && isWorkday) filtered.add(r);
            if ("weekend".equals(dayType) && !isWorkday) filtered.add(r);
        }
        return filtered;
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
