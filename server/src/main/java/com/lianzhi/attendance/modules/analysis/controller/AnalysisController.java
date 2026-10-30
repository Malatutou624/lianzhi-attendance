package com.lianzhi.attendance.modules.analysis.controller;

import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.analysis.service.HeatmapService;
import com.lianzhi.attendance.modules.analysis.service.PredictService;
import com.lianzhi.attendance.modules.analysis.service.VenueFlowService;
import com.lianzhi.attendance.modules.analysis.service.WorktimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数据分析看板统一入口（一级菜单：数据分析看板）
 *
 * Tab1 场所人流：   /analysis/venueFlow            时序趋势+峰值谷值均值
 *                  /analysis/venueFlow/detail     时段在场人员明细（穿透）
 *                  /analysis/venueFlow/export     导出Excel
 * Tab2 考勤工时：   /analysis/worktime             员工汇总+部门柱状图+异常饼图
 *                  /analysis/worktime/detail      原始打卡+审批单据明细（穿透）
 *                  /analysis/worktime/export      导出Excel
 * Tab3 趋势预测：   /analysis/predict              历史+预测双曲线
 *                  /analysis/predict/export       导出Excel
 * Tab4 热力画像：   /analysis/heatmap              点位密度（坐标+权重）
 *                  /analysis/heatmap/detail       点位人员名单与停留时段（穿透）
 *                  /analysis/heatmap/export       导出Excel
 *
 * 所有统计实时读原始表（checkin_record / approval_record），
 * 打卡或审批数据一变，下次查询自动就是最新结果（满足"自动重新计算"要求）
 */
@RestController
@RequestMapping("/analysis")
public class AnalysisController {

    @Autowired
    private VenueFlowService venueFlowService;

    @Autowired
    private WorktimeService worktimeService;

    @Autowired
    private PredictService predictService;

    @Autowired
    private HeatmapService heatmapService;

    // ==================== Tab1 场所人流分析 ====================

    @GetMapping("/venueFlow")
    public Result<Map<String, Object>> venueFlow(
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String dayType) {
        LocalDate[] range = dateRange(start, end, 7);
        return Result.ok(venueFlowService.flow(venueId, range[0], range[1], dayType));
    }

    @GetMapping("/venueFlow/detail")
    public Result<List<Map<String, Object>>> venueFlowDetail(
            @RequestParam(required = false) Long venueId,
            @RequestParam String slot) {
        return Result.ok(venueFlowService.detail(venueId, slot));
    }

    @GetMapping("/venueFlow/export")
    public ResponseEntity<byte[]> venueFlowExport(
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String dayType) {
        LocalDate[] range = dateRange(start, end, 7);
        byte[] bytes = venueFlowService.export(venueId, range[0], range[1], dayType);
        return file(bytes, "场所人流分析.xlsx");
    }

    // ==================== Tab2 员工考勤工时分析 ====================

    @GetMapping("/worktime")
    public Result<Map<String, Object>> worktime(
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        LocalDate[] range = monthRange(start, end);
        return Result.ok(worktimeService.summary(dept, venueId, range[0], range[1]));
    }

    @GetMapping("/worktime/detail")
    public Result<Map<String, Object>> worktimeDetail(
            @RequestParam Long empId,
            @RequestParam String start,
            @RequestParam String end) {
        return Result.ok(worktimeService.detail(empId,
                LocalDate.parse(start), LocalDate.parse(end)));
    }

    @GetMapping("/worktime/export")
    public ResponseEntity<byte[]> worktimeExport(
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        LocalDate[] range = monthRange(start, end);
        byte[] bytes = worktimeService.export(dept, venueId, range[0], range[1]);
        return file(bytes, "考勤工时汇总.xlsx");
    }

    // ==================== Tab3 考勤缺勤趋势预测 ====================

    @GetMapping("/predict")
    public Result<Map<String, Object>> predict(
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) Long venueId,
            @RequestParam(defaultValue = "absent") String type,
            @RequestParam(defaultValue = "7") int days) {
        return Result.ok(predictService.predict(dept, venueId, type, days));
    }

    @GetMapping("/predict/export")
    public ResponseEntity<byte[]> predictExport(
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) Long venueId,
            @RequestParam(defaultValue = "absent") String type,
            @RequestParam(defaultValue = "7") int days) {
        byte[] bytes = predictService.export(dept, venueId, type, days);
        return file(bytes, "考勤趋势预测.xlsx");
    }

    // ==================== Tab4 场所人员热力画像 ====================

    @GetMapping("/heatmap")
    public Result<Map<String, Object>> heatmap(
            @RequestParam Long venueId,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(defaultValue = "all") String period) {
        LocalDate[] range = dateRange(start, end, 7);
        return Result.ok(heatmapService.heatmap(venueId, range[0], range[1], period));
    }

    @GetMapping("/heatmap/detail")
    public Result<List<Map<String, Object>>> heatmapDetail(
            @RequestParam Long venueId,
            @RequestParam Double lng,
            @RequestParam Double lat,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(defaultValue = "all") String period) {
        LocalDate[] range = dateRange(start, end, 7);
        return Result.ok(heatmapService.detail(venueId, lng, lat, range[0], range[1], period));
    }

    /** 场所时段热力图：星期几 × 小时 的平均在场密度 */
    @GetMapping("/heatmap/timeHeatmap")
    public Result<Map<String, Object>> timeHeatmap(
            @RequestParam Long venueId,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        LocalDate[] range = dateRange(start, end, 7);
        return Result.ok(heatmapService.timeHeatmap(venueId, range[0], range[1]));
    }

    @GetMapping("/heatmap/export")
    public ResponseEntity<byte[]> heatmapExport(
            @RequestParam Long venueId,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(defaultValue = "all") String period) {
        LocalDate[] range = dateRange(start, end, 7);
        byte[] bytes = heatmapService.export(venueId, range[0], range[1], period);
        return file(bytes, "场所热力点位.xlsx");
    }

    // ==================== 公共：全局筛选项 ====================

    /** 部门下拉列表（全局筛选通用） */
    @GetMapping("/depts")
    public Result<List<String>> depts() {
        java.util.TreeSet<String> depts = new java.util.TreeSet<>();
        worktimeService.allDepts().forEach(d -> { if (d != null && !d.isEmpty()) depts.add(d); });
        return Result.ok(new java.util.ArrayList<>(depts));
    }

    // ==================== 私有工具 ====================

    /** 解析日期范围：未传时默认最近 N 天（含今天） */
    private static LocalDate[] dateRange(String start, String end, int defaultDays) {
        LocalDate s = (start == null || start.isEmpty())
                ? LocalDate.now().minusDays(defaultDays - 1L) : LocalDate.parse(start);
        LocalDate e = (end == null || end.isEmpty()) ? LocalDate.now() : LocalDate.parse(end);
        return new LocalDate[]{s, e};
    }

    /** 考勤工时用的日期范围：未传时默认本月1号到今天 */
    private static LocalDate[] monthRange(String start, String end) {
        LocalDate s = (start == null || start.isEmpty())
                ? LocalDate.now().withDayOfMonth(1) : LocalDate.parse(start);
        LocalDate e = (end == null || end.isEmpty()) ? LocalDate.now() : LocalDate.parse(end);
        return new LocalDate[]{s, e};
    }

    /** 把字节流包装成 xlsx 下载响应（文件名 URL 编码防中文乱码） */
    private static ResponseEntity<byte[]> file(byte[] bytes, String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encoded);
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
