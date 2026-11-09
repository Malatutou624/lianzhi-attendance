package com.lianzhi.attendance.modules.h5.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.analysis.service.AttendanceStatusService;
import com.lianzhi.attendance.modules.checkin.entity.CheckinRecord;
import com.lianzhi.attendance.modules.checkin.service.CheckinService;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.service.EmployeeService;
import com.lianzhi.attendance.modules.rule.entity.AttendRule;
import com.lianzhi.attendance.modules.rule.service.RuleService;
import com.lianzhi.attendance.modules.venue.entity.Venue;
import com.lianzhi.attendance.modules.venue.service.VenueService;
import com.lianzhi.attendance.modules.h5.entity.H5Account;
import com.lianzhi.attendance.modules.h5.mapper.H5AccountMapper;
import com.lianzhi.attendance.modules.face.service.FaceMatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * H5 员工端接口（手机浏览器访问，员工没有管理端账号，所以这些接口不需要登录 token）
 * 注意：这些路径都在 /h5 下，WebConfig 拦截器已放行 /h5/**
 *
 * GET  /h5/venue/list      场所下拉列表（采集页用）
 * GET  /h5/venue/{id}      查单个场所信息（打卡页顶部显示场所名）
 * POST /h5/checkin         打卡（核心接口）
 * POST /h5/collect         员工信息采集（扫码后填写姓名工号+人脸照片）
 */
@RestController
@RequestMapping("/h5")
public class H5Controller {

    private static final Logger log = LoggerFactory.getLogger(H5Controller.class);

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private VenueService venueService;

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private RuleService ruleService;

    @Autowired
    private com.lianzhi.attendance.modules.analysis.service.AttendanceStatusService statusService;

    @Autowired
    private H5AccountMapper accountMapper;

    @Autowired
    private FaceMatchService faceMatchService;

    /** Redis 操作工具：用来做"60秒内防重复打卡"的锁 */
    @Autowired
    private StringRedisTemplate redisTemplate;

    /** 场所下拉列表（只要启用的场所，采集页选择所属场所用） */
    @GetMapping("/venue/list")
    public Result<List<Venue>> venueList() {
        List<Venue> list = venueService.list(
                Wrappers.<Venue>lambdaQuery().eq(Venue::getStatus, 1).orderByAsc(Venue::getId));
        // 出于安全考虑，不下发经纬度给采集页也可以，但打卡页需要坐标做调试兜底，这里直接全部返回
        return Result.ok(list);
    }

    /** 查单个场所：打卡页打开时显示"XX公司 · XX场所" */
    @GetMapping("/venue/{id}")
    public Result<Venue> venueInfo(@PathVariable Long id) {
        Venue venue = venueService.getById(id);
        if (venue == null || venue.getStatus() != 1) {
            return Result.fail("场所不存在或已停用");
        }
        return Result.ok(venue);
    }

    /** 我的打卡记录列表（H5 端用，自动根据 h5AccountId 取绑定员工） */
    @GetMapping("/checkin/list")
    public Result<Page<CheckinRecord>> myCheckins(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        Long accountId = (Long) request.getAttribute("h5AccountId");
        H5Account acc = accountMapper.selectById(accountId);
        if (acc == null || acc.getEmpId() == null) {
            return Result.fail("请先完成员工信息采集");
        }
        Page<CheckinRecord> page = checkinService.page(new Page<>(current, size),
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(CheckinRecord::getEmpId, acc.getEmpId())
                        .orderByDesc(CheckinRecord::getSignInTime));
        return Result.ok(page);
    }

    /**
     * H5 月度考勤汇总（自动根据 token 取绑定员工）
     * GET /h5/stats?month=2026-10  不传 month 默认当月
     * 返回：attendDays 出勤天数 / lateCount 迟到次数 / earlyCount 早退次数
     *       totalHours 累计工时 / workDays 应出勤工作日数 / absentCount 缺勤天数
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> monthStats(
            HttpServletRequest request,
            @RequestParam(required = false) String month) {
        Long empId = resolveEmpId(request);
        if (empId == null) return Result.fail("请先登录并完成员工信息采集");

        YearMonth ym = (month != null && !month.isEmpty()) ? YearMonth.parse(month) : YearMonth.now();
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();

        List<CheckinRecord> records = checkinService.list(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(CheckinRecord::getEmpId, empId)
                        .ge(CheckinRecord::getSignInTime, start)
                        .lt(CheckinRecord::getSignInTime, end));

        // 出勤天数 = 有签到的记录数
        long attendDays = records.stream().filter(r -> r.getSignInTime() != null).count();
        // 迟到次数 = late + lateEarly（既迟到又早退那天也计入迟到）
        long lateCount = records.stream().filter(r ->
                "late".equals(r.getStatus()) || "lateEarly".equals(r.getStatus())).count();
        // 早退次数 = early + lateEarly（既迟到又早退那天也计入早退）
        long earlyCount = records.stream().filter(r ->
                "early".equals(r.getStatus()) || "lateEarly".equals(r.getStatus())).count();

        // 累计工时 = 每条记录 signIn→signOut 的分钟数之和 / 60
        double totalHours = 0;
        for (CheckinRecord r : records) {
            if (r.getSignInTime() != null && r.getSignOutTime() != null) {
                long mins = Duration.between(r.getSignInTime(), r.getSignOutTime()).toMinutes();
                totalHours += mins / 60.0;
            }
        }
        BigDecimal hoursBd = BigDecimal.valueOf(totalHours).setScale(2, RoundingMode.HALF_UP);

        // 应出勤工作日数 = 当月周一~周五的天数
        int workDays = 0;
        for (int d = 1; d <= ym.lengthOfMonth(); d++) {
            LocalDate date = ym.atDay(d);
            int dow = date.getDayOfWeek().getValue(); // 1=Monday, 7=Sunday
            if (dow <= 5) workDays++;
        }
        long absentCount = Math.max(workDays - attendDays, 0);

        Map<String, Object> data = new HashMap<>();
        data.put("attendDays", attendDays);
        data.put("lateCount", lateCount);
        data.put("earlyCount", earlyCount);
        data.put("totalHours", hoursBd);
        data.put("workDays", workDays);
        data.put("absentCount", absentCount);
        data.put("yearMonth", ym.toString());
        return Result.ok(data);
    }

    /**
     * H5 月度打卡明细（日历用，自动根据 token 取绑定员工）
     * GET /h5/checkin/month?month=2026-10  不传默认当月
     * 返回 records 列表，每条带 workHours 字段（BigDecimal，后端算好）
     */
    @GetMapping("/checkin/month")
    public Result<List<Map<String, Object>>> monthCheckins(
            HttpServletRequest request,
            @RequestParam(required = false) String month) {
        Long empId = resolveEmpId(request);
        if (empId == null) return Result.fail("请先登录并完成员工信息采集");

        YearMonth ym = (month != null && !month.isEmpty()) ? YearMonth.parse(month) : YearMonth.now();
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();

        List<CheckinRecord> records = checkinService.list(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(CheckinRecord::getEmpId, empId)
                        .ge(CheckinRecord::getSignInTime, start)
                        .lt(CheckinRecord::getSignInTime, end)
                        .orderByAsc(CheckinRecord::getSignInTime));

        // 预加载 venue 名称
        List<Long> venueIds = records.stream().map(CheckinRecord::getVenueId)
                .filter(Objects::nonNull).distinct().collect(java.util.stream.Collectors.toList());
        Map<Long, String> venueMap = venueIds.isEmpty() ? Collections.emptyMap()
                : venueService.listByIds(venueIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Venue::getId, Venue::getName));

        List<Map<String, Object>> out = new ArrayList<>();
        for (CheckinRecord r : records) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", r.getId());
            item.put("signInTime", r.getSignInTime() == null ? null : r.getSignInTime().toString());
            item.put("signOutTime", r.getSignOutTime() == null ? null : r.getSignOutTime().toString());
            item.put("status", r.getStatus());
            item.put("venueName", venueMap.get(r.getVenueId()));

            // 工时（分钟精度，保留 2 位小数）
            BigDecimal workHours = BigDecimal.ZERO;
            if (r.getSignInTime() != null && r.getSignOutTime() != null) {
                long mins = Duration.between(r.getSignInTime(), r.getSignOutTime()).toMinutes();
                workHours = BigDecimal.valueOf(mins).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            }
            item.put("workHours", workHours);
            out.add(item);
        }
        return Result.ok(out);
    }

    /** 从 request 中解析绑定的员工 ID（H5 token → h5AccountId → empId） */
    private Long resolveEmpId(HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("h5AccountId");
        if (accountId == null) return null;
        H5Account acc = accountMapper.selectById(accountId);
        return acc == null ? null : acc.getEmpId();
    }

    /**
     * 打卡接口（本系统最核心的接口）
     * 前端传 JSON：{venueId, empNo, longitude, latitude, photoUrl, deviceInfo}
     *
     * 处理步骤（顺序不要乱）：
     *   1. 校验员工：按工号查，必须存在且在职
     *   2. 校验场所：必须存在、启用、且配置了经纬度
     *   3. 距离校验：手机GPS坐标 与 场所坐标 的距离 ≤ 场所允许半径
     *   4. 防重复：Redis 锁，同一员工60秒内只能打一次
     *   5. 上下班判定：今天第一次打卡=上班卡（晚了算迟到）；之后的打卡=下班卡（早了算早退）
     *   6. 写入打卡记录表
     */
    @PostMapping("/checkin")
    public Result<Map<String, Object>> checkin(@RequestBody Map<String, Object> params,
                                               HttpServletRequest request) {
        // ---------- 0. 校验登录 + 绑定 ----------
        Long accountId = (Long) request.getAttribute("h5AccountId");
        H5Account acc = accountMapper.selectById(accountId);
        if (acc == null || acc.getEmpId() == null) {
            return Result.fail("请先完成员工信息采集");
        }
        // 强制用绑定的员工，防止前端篡改 empNo
        Employee emp = employeeService.getById(acc.getEmpId());
        if (emp == null) return Result.fail("绑定的员工信息不存在");
        if (emp.getStatus() != 1) return Result.fail("该员工已停用，请联系管理员");

        Long venueId = params.get("venueId") == null ? null : Long.valueOf(params.get("venueId").toString());
        BigDecimal longitude = params.get("longitude") == null ? null : new BigDecimal(params.get("longitude").toString());
        BigDecimal latitude = params.get("latitude") == null ? null : new BigDecimal(params.get("latitude").toString());
        String photoUrl = params.get("photoUrl") == null ? null : params.get("photoUrl").toString();
        String deviceInfo = params.get("deviceInfo") == null ? null : params.get("deviceInfo").toString();

        if (venueId == null) return Result.fail("缺少必要参数：场所");

        // ---------- 1. 人脸识别校验（强制）----------
        // 采集时的模板照 + 打卡时的现场照 → 感知哈希比对
        if (StringUtils.isBlank(emp.getFacePhoto()) || StringUtils.isBlank(emp.getFaceFeature())) {
            return Result.fail("未采集人脸照片，请先完成信息采集");
        }
        if (StringUtils.isBlank(photoUrl)) {
            return Result.fail("请先拍摄现场人脸照片（点击打卡区的拍照按钮）");
        }
        FaceMatchService.MatchResult match = faceMatchService.compare(emp.getFacePhoto(), photoUrl);
        if (!match.match) {
            // 给前端返回带相似度的详细信息，方便前端展示
            String detail = String.format("（相似度 %.0f%%，汉明距离 %d/%d）",
                    match.similarity * 100, match.hammingDistance, FaceMatchService.SAME_PERSON_THRESHOLD);
            log.warn("[checkin] 人脸比对失败: emp={}, distance={}, similarity={}",
                    emp.getName(), match.hammingDistance, match.similarity);
            return Result.fail("人脸识别未通过，请本人正对镜头重新拍照" + detail);
        }

        // ---------- 2. 校验场所 ----------
        Venue venue = venueService.getById(venueId);
        if (venue == null || venue.getStatus() != 1) {
            return Result.fail("场所不存在或已停用");
        }
        if (venue.getLongitude() == null || venue.getLatitude() == null) {
            return Result.fail("该场所还没有配置定位坐标，请管理员在【办公场所】中完善");
        }

        // ---------- 3. 距离校验 ----------
        if (longitude == null || latitude == null) {
            return Result.fail("未获取到手机定位，请允许浏览器使用定位后重试");
        }
        double distance = distanceMeters(
                latitude.doubleValue(), longitude.doubleValue(),
                venue.getLatitude().doubleValue(), venue.getLongitude().doubleValue());
        int allowRadius = venue.getRadius() == null ? 300 : venue.getRadius();
        if (distance > allowRadius) {
            return Result.fail("不在打卡范围内：距离 " + venue.getName()
                    + " 约 " + (int) distance + " 米，允许范围 " + allowRadius + " 米");
        }

        // ---------- 4. 防重复打卡（Redis 锁）----------
        // setIfAbsent = SETNX：key 不存在才能设置成功；给 key 加60秒过期时间
        // 60秒内第二次打卡时 key 还在 → 设置失败 → 拒绝
        String lockKey = "checkin:lock:" + emp.getId();
        Boolean firstIn60s = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", 60, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(firstIn60s)) {
            return Result.fail("60秒内请勿重复打卡");
        }

        // ---------- 5. 上下班判定（统一走 AttendanceStatusService 7 种互斥口径）----------
        // 第一次打卡（签到）：先按签到时间写临时状态，签退后再综合判定
        // 第二次打卡（签退）：用 judgeByRecord 算出最终综合状态
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        List<CheckinRecord> todayRecords = checkinService.list(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(CheckinRecord::getEmpId, emp.getId())
                        .ge(CheckinRecord::getSignInTime, startOfToday)
                        .orderByAsc(CheckinRecord::getSignInTime));
        long todayCount = todayRecords.size();
        AttendRule rule = ruleService.getByVenueId(venueId);
        String status;       // 7 种互斥状态之一
        String punchType;
        if (todayCount == 0) {
            punchType = "上班卡";
            // 暂时按签到时间判，签退后会覆盖成最终状态
            status = LocalTime.now().isAfter(rule.getWorkStart().plusMinutes(rule.getLateMinutes()))
                    ? AttendanceStatusService.LATE : AttendanceStatusService.NORMAL;
        } else if (todayCount == 1) {
            punchType = "下班卡";
            // 综合签到+签退算最终状态
            CheckinRecord first = todayRecords.get(0);
            first.setSignOutTime(LocalDateTime.now());
            status = statusService.judgeByRecord(first, rule);
        } else {
            return Result.fail("今日已完成签到和签退，请勿重复打卡");
        }

        // ---------- 6. 写入或更新打卡记录 ----------
        CheckinRecord record;
        if (todayCount == 0) {
            // 第一次打卡：插入新记录（签到）
            record = new CheckinRecord();
            record.setEmpId(emp.getId());
            record.setVenueId(venueId);
            record.setSignInTime(LocalDateTime.now());
            record.setCheckinType("photo");
            record.setPhotoUrl(photoUrl);
            record.setLongitude(longitude);
            record.setLatitude(latitude);
            record.setDeviceInfo(deviceInfo);
            record.setStatus(status);
            record.setSimilarity(new java.math.BigDecimal(match.similarity * 100).setScale(2, java.math.RoundingMode.HALF_UP));
            checkinService.save(record);
        } else {
            // 第二次打卡：更新记录（签退）
            record = todayRecords.get(0);
            record.setSignOutTime(LocalDateTime.now());
            record.setSignOutPhoto(photoUrl);
            record.setStatus(status);
            record.setSimilarity(new java.math.BigDecimal(match.similarity * 100).setScale(2, java.math.RoundingMode.HALF_UP));
            checkinService.updateById(record);
        }

        // ---------- 7. 返回结果给手机页面 ----------
        Map<String, Object> data = new HashMap<>();
        data.put("empName", emp.getName());
        data.put("venueName", venue.getName());
        data.put("time", LocalDateTime.now().toString().replace("T", " ").substring(0, 19));
        data.put("status", status);
        data.put("punchType", punchType);
        data.put("distance", (int) distance);
        String statusText = "normal".equals(status) ? "正常" : ("late".equals(status) ? "迟到" : "早退");
        data.put("statusText", statusText);
        return Result.ok(data);
    }

    /**
     * 员工信息采集：员工扫场所码后，填写 工号/姓名/手机号/部门 + 上传人脸照片
     * 要求：一个账号只能采集一次，采集后账号自动绑定该员工
     */
    @PostMapping("/collect")
    public Result<Map<String, Object>> collect(@RequestBody Employee employee,
                                                HttpServletRequest request) {
        // ---------- 0. 校验账号 ----------
        Long accountId = (Long) request.getAttribute("h5AccountId");
        H5Account acc = accountMapper.selectById(accountId);
        if (acc == null) return Result.fail("请先登录");
        if (acc.getEmpId() != null) {
            return Result.fail("本账号已完成过员工信息采集，如需修改请联系管理员");
        }

        // 必填校验
        if (StringUtils.isBlank(employee.getEmpNo()) || StringUtils.isBlank(employee.getName())) {
            return Result.fail("工号和姓名不能为空");
        }
        // 工号唯一校验
        long count = employeeService.count(
                Wrappers.<Employee>lambdaQuery().eq(Employee::getEmpNo, employee.getEmpNo()));
        if (count > 0) {
            return Result.fail("工号 " + employee.getEmpNo() + " 已被其他账号采集过，请联系管理员");
        }

        // 默认在职，保存员工
        employee.setStatus(1);
        employeeService.save(employee);

        // 自动计算人脸特征（pHash）存到 faceFeature 字段
        // 比对时会用它与打卡现场照做相似度校验
        if (StringUtils.isNotBlank(employee.getFacePhoto())) {
            try {
                String hash = faceMatchService.computeHash(employee.getFacePhoto());
                if (hash != null) {
                    employee.setFaceFeature(hash);
                    employeeService.updateById(employee);
                }
            } catch (Exception e) {
                // 算失败不阻塞采集，后面打卡时会提示"未采集人脸"
                log.warn("[collect] 人脸特征计算失败（不阻塞采集）: {}", e.getMessage());
            }
        }

        // 账号绑定该员工（唯一绑定）
        acc.setEmpId(employee.getId());
        accountMapper.updateById(acc);

        Map<String, Object> data = new HashMap<>();
        data.put("id", employee.getId());
        data.put("empNo", employee.getEmpNo());
        data.put("name", employee.getName());
        return Result.ok(data);
    }

    /**
     * 计算两个经纬度坐标之间的距离（单位：米）
     * 用的是 Haversine 公式（把地球当球体算球面距离），精度对打卡场景足够
     * 公式不用背，知道"输入两个坐标 → 输出距离（米）"就行
     */
    private double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        final double EARTH_RADIUS = 6371000; // 地球半径，单位米
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(radLat1) * Math.cos(radLat2)
                * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }
}
