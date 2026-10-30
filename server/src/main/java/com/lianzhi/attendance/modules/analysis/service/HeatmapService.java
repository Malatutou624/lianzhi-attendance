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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 子功能4：办公场所人员热力画像
 *
 * 统计口径：
 * - 数据源：checkin_record 打卡时采集的经纬度（GPS定位）
 * - 点位聚合：把经纬度按 4 位小数（约11米）切成网格，一个格子 = 一个"点位"，
 *   权重 = 该点位覆盖到的去重员工数（停留密度）
 * - 时段筛选：morning上午(0~12点) / afternoon下午(12~24点) / all全天，按签到时间判断
 * - 输出坐标+权重数组，前端用 ECharts 热力图渲染，无需地图SDK即可演示
 */
@Service
public class HeatmapService {

    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private VenueService venueService;

    /**
     * 点位密度数据
     * @param venueId 场所ID（必填，热力图需要确定范围）
     * @param period  all / morning / afternoon
     * @return points: [{lng, lat, weight}]，bounds: 经纬度范围
     */
    public Map<String, Object> heatmap(Long venueId, LocalDate start, LocalDate end, String period) {
        List<CheckinRecord> records = query(venueId, start, end, period);

        // 网格聚合：cellKey(4位小数经纬度) -> 去重员工
        Map<String, Set<Long>> cellEmps = new HashMap<>();
        Map<String, double[]> cellCenter = new HashMap<>();
        for (CheckinRecord r : records) {
            if (r.getLongitude() == null || r.getLatitude() == null) continue;
            double lng = r.getLongitude().doubleValue();
            double lat = r.getLatitude().doubleValue();
            String key = round4(lng) + "," + round4(lat);
            cellEmps.computeIfAbsent(key, k -> new HashSet<>()).add(r.getEmpId());
            cellCenter.putIfAbsent(key, new double[]{round4(lng), round4(lat)});
        }

        List<Map<String, Object>> points = new ArrayList<>();
        double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        for (Map.Entry<String, Set<Long>> e : cellEmps.entrySet()) {
            double[] c = cellCenter.get(e.getKey());
            Map<String, Object> p = new HashMap<>();
            p.put("lng", c[0]);
            p.put("lat", c[1]);
            p.put("weight", e.getValue().size());
            points.add(p);
            minLng = Math.min(minLng, c[0]); maxLng = Math.max(maxLng, c[0]);
            minLat = Math.min(minLat, c[1]); maxLat = Math.max(maxLat, c[1]);
        }
        // 只有一个点或没数据时给个默认范围，避免前端渲染除零
        if (points.size() <= 1) { minLng = maxLng = points.isEmpty() ? 0 : minLng; minLat = maxLat = points.isEmpty() ? 0 : minLat; }

        Map<String, Object> bounds = new HashMap<>();
        bounds.put("minLng", minLng); bounds.put("maxLng", maxLng);
        bounds.put("minLat", minLat); bounds.put("maxLat", maxLat);

        Map<String, Object> result = new HashMap<>();
        result.put("points", points);
        result.put("bounds", bounds);
        return result;
    }

    /**
     * 点位下钻：某个网格点位的人员名单与停留时段
     * @param lng/lat 点位坐标（4位小数网格中心）
     */
    public List<Map<String, Object>> detail(Long venueId, Double lng, Double lat,
                                            LocalDate start, LocalDate end, String period) {
        List<CheckinRecord> records = query(venueId, start, end, period);

        Map<Long, Employee> empMap = new HashMap<>();
        for (Employee e : employeeService.list()) empMap.put(e.getId(), e);
        Map<Long, String> venueNames = new HashMap<>();
        for (Venue v : venueService.list()) venueNames.put(v.getId(), v.getName());

        List<Map<String, Object>> list = new ArrayList<>();
        for (CheckinRecord r : records) {
            if (r.getLongitude() == null || r.getLatitude() == null) continue;
            // 只保留落在目标网格内的打卡
            if (round4(r.getLongitude().doubleValue()) != round4(lng)
                    || round4(r.getLatitude().doubleValue()) != round4(lat)) continue;
            Employee e = empMap.get(r.getEmpId());
            Map<String, Object> item = new HashMap<>();
            item.put("name", e == null ? "未知员工" : e.getName());
            item.put("dept", e == null ? "-" : e.getDept());
            item.put("venueName", venueNames.getOrDefault(r.getVenueId(), "-"));
            item.put("signInTime", r.getSignInTime() == null ? "-" : r.getSignInTime().format(F));
            item.put("signOutTime", r.getSignOutTime() == null ? "未签退" : r.getSignOutTime().format(F));
            list.add(item);
        }
        list.sort(Comparator.comparing(a -> a.get("signInTime").toString()));
        return list;
    }

    /** 导出Excel：点位密度列表 */
    public byte[] export(Long venueId, LocalDate start, LocalDate end, String period) {
        Map<String, Object> data = heatmap(venueId, start, end, period);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) data.get("points");

        String[] headers = {"经度", "纬度", "人数（权重）"};
        List<Object[]> rows = new ArrayList<>();
        for (Map<String, Object> p : points) {
            rows.add(new Object[]{p.get("lng"), p.get("lat"), p.get("weight")});
        }
        return ExcelUtil.build("场所热力点位", headers, rows);
    }

    /**
     * 场所时段热力图：星期几 × 小时 的平均在场密度
     * 口径：每条打卡记录视为员工在 [签到, 签退] 内在场（签退空按当天 22:00），
     * 先算每个自然日每个小时的去重在场人数，再按星期几求均值
     * （范围内有几个星期一就除几），保留 1 位小数
     * @return hours: [8..19]；days: [周一..周日]；data: [{day(1-7), hour, value}]；max: 最大值
     */
    public Map<String, Object> timeHeatmap(Long venueId, LocalDate start, LocalDate end) {
        final int HOUR_START = 8, HOUR_END = 19; // 与展示图一致：08~19 时段
        List<CheckinRecord> records = query(venueId, start, end, "all");

        // 1. 每个 (自然日, 小时) 的去重在场人数
        Map<String, Set<Long>> dayHourEmps = new HashMap<>(); // "2026-10-28|9" -> empIds
        for (CheckinRecord r : records) {
            java.time.LocalDateTime in = r.getSignInTime();
            java.time.LocalDateTime out = r.getSignOutTime() != null
                    ? r.getSignOutTime() : r.getSignInTime().toLocalDate().atTime(22, 0);
            LocalDate day = in.toLocalDate();
            for (int h = HOUR_START; h <= HOUR_END; h++) {
                java.time.LocalDateTime slotStart = day.atTime(h, 0);
                java.time.LocalDateTime slotEnd = slotStart.plusHours(1);
                if (in.isBefore(slotEnd) && out.isAfter(slotStart)) {
                    dayHourEmps.computeIfAbsent(day + "|" + h, k -> new HashSet<>()).add(r.getEmpId());
                }
            }
        }

        // 2. 范围内每个星期几出现的天数（求均值的分母）
        Map<java.time.DayOfWeek, Integer> dowDays = new EnumMap<>(java.time.DayOfWeek.class);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dowDays.merge(d.getDayOfWeek(), 1, Integer::sum);
        }

        // 3. 按 (星期几, 小时) 汇总后求平均
        Map<String, Integer> dowHourTotal = new HashMap<>(); // "1|9" -> 人次合计（1=周一）
        for (Map.Entry<String, Set<Long>> e : dayHourEmps.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            LocalDate day = LocalDate.parse(parts[0]);
            String key = day.getDayOfWeek().getValue() + "|" + parts[1];
            dowHourTotal.merge(key, e.getValue().size(), Integer::sum);
        }

        List<Map<String, Object>> data = new ArrayList<>();
        double max = 0;
        for (int dow = 1; dow <= 7; dow++) {
            int divisor = dowDays.getOrDefault(java.time.DayOfWeek.of(dow), 1);
            for (int h = HOUR_START; h <= HOUR_END; h++) {
                double v = dowHourTotal.getOrDefault(dow + "|" + h, 0) * 1.0 / divisor;
                v = Math.round(v * 10) / 10.0;
                Map<String, Object> cell = new HashMap<>();
                cell.put("day", dow);
                cell.put("hour", h);
                cell.put("value", v);
                data.add(cell);
                if (v > max) max = v;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("hourStart", HOUR_START);
        result.put("hourEnd", HOUR_END);
        result.put("data", data);
        result.put("max", max);
        return result;
    }

    /** 查询符合条件的打卡记录（含坐标、时段过滤） */
    private List<CheckinRecord> query(Long venueId, LocalDate start, LocalDate end, String period) {
        List<CheckinRecord> records = checkinService.list(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(venueId != null, CheckinRecord::getVenueId, venueId)
                        .ge(CheckinRecord::getSignInTime, start.atStartOfDay())
                        .lt(CheckinRecord::getSignInTime, end.plusDays(1).atStartOfDay())
                        .isNotNull(CheckinRecord::getSignInTime));

        if (period == null || "all".equals(period)) return records;
        List<CheckinRecord> filtered = new ArrayList<>();
        for (CheckinRecord r : records) {
            int h = r.getSignInTime().getHour();
            if ("morning".equals(period) && h < 12) filtered.add(r);
            if ("afternoon".equals(period) && h >= 12) filtered.add(r);
        }
        return filtered;
    }

    /** 保留4位小数（约11米网格），double 比较安全 */
    private static double round4(double v) {
        return Math.round(v * 10000) / 10000.0;
    }
}
