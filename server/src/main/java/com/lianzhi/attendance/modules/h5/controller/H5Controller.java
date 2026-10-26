package com.lianzhi.attendance.modules.h5.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.checkin.entity.CheckinRecord;
import com.lianzhi.attendance.modules.checkin.service.CheckinService;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.service.EmployeeService;
import com.lianzhi.attendance.modules.rule.entity.AttendRule;
import com.lianzhi.attendance.modules.rule.service.RuleService;
import com.lianzhi.attendance.modules.venue.entity.Venue;
import com.lianzhi.attendance.modules.venue.service.VenueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private VenueService venueService;

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private RuleService ruleService;

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
    public Result<Map<String, Object>> checkin(@RequestBody Map<String, Object> params) {
        // ---------- 0. 取参数 ----------
        Long venueId = params.get("venueId") == null ? null : Long.valueOf(params.get("venueId").toString());
        String empNo = params.get("empNo") == null ? null : params.get("empNo").toString().trim();
        BigDecimal longitude = params.get("longitude") == null ? null : new BigDecimal(params.get("longitude").toString());
        BigDecimal latitude = params.get("latitude") == null ? null : new BigDecimal(params.get("latitude").toString());
        String photoUrl = params.get("photoUrl") == null ? null : params.get("photoUrl").toString();
        String deviceInfo = params.get("deviceInfo") == null ? null : params.get("deviceInfo").toString();

        if (venueId == null || StringUtils.isBlank(empNo)) {
            return Result.fail("缺少必要参数：场所或工号");
        }

        // ---------- 1. 校验员工 ----------
        Employee emp = employeeService.getOne(
                Wrappers.<Employee>lambdaQuery().eq(Employee::getEmpNo, empNo));
        if (emp == null) {
            return Result.fail("工号不存在：" + empNo + "，请先扫码完成信息采集");
        }
        if (emp.getStatus() != 1) {
            return Result.fail("该员工已停用，请联系管理员");
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

        // ---------- 5. 查询今天是否已有打卡记录 ----------
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime endOfToday = startOfToday.plusDays(1);
        CheckinRecord todayRecord = checkinService.getOne(
                Wrappers.<CheckinRecord>lambdaQuery()
                        .eq(CheckinRecord::getEmpId, emp.getId())
                        .ge(CheckinRecord::getSignInTime, startOfToday)
                        .lt(CheckinRecord::getSignInTime, endOfToday));

        // 查该场所的考勤规则（没配置就用默认 9:00/18:00/宽限10分钟）
        AttendRule rule = ruleService.getByVenueId(venueId);
        LocalTime nowTime = LocalTime.now();
        LocalDateTime now = LocalDateTime.now();
        String status;
        String punchType;

        if (todayRecord == null) {
            // ---------- 6a. 今天第一次打卡 → 签到 ----------
            punchType = "签到";
            status = "normal";
            if (nowTime.isAfter(rule.getWorkStart().plusMinutes(rule.getLateMinutes()))) {
                status = "late";
            }
            CheckinRecord record = new CheckinRecord();
            record.setEmpId(emp.getId());
            record.setVenueId(venueId);
            record.setSignInTime(now);
            record.setCheckinType("photo");
            record.setPhotoUrl(photoUrl);
            record.setLongitude(longitude);
            record.setLatitude(latitude);
            record.setDeviceInfo(deviceInfo);
            record.setStatus(status);
            checkinService.save(record);
        } else if (todayRecord.getSignOutTime() == null) {
            // ---------- 6b. 已有签到，未签退 → 签退 ----------
            punchType = "签退";
            status = todayRecord.getStatus(); // 保留签到时的状态
            if (nowTime.isBefore(rule.getWorkEnd())) {
                status = "early";
            }
            todayRecord.setSignOutTime(now);
            todayRecord.setSignOutPhoto(photoUrl);
            todayRecord.setStatus(status);
            checkinService.updateById(todayRecord);
        } else {
            // ---------- 6c. 今天已签到且已签退 → 拒绝 ----------
            return Result.fail("今日已完成签到和签退，请勿重复打卡");
        }

        // ---------- 7. 返回结果给手机页面 ----------
        Map<String, Object> data = new HashMap<>();
        data.put("empName", emp.getName());
        data.put("venueName", venue.getName());
        data.put("time", now.toString().replace("T", " ").substring(0, 19));
        data.put("status", status);
        data.put("punchType", punchType);
        data.put("distance", (int) distance);
        String statusText = "normal".equals(status) ? "正常" : ("late".equals(status) ? "迟到" : "早退");
        data.put("statusText", statusText);
        return Result.ok(data);
    }

    /**
     * 员工信息采集：员工扫场所码后，填写 工号/姓名/手机号/部门 + 上传人脸照片
     * 前端传 JSON：{empNo, name, phone, gender, dept, venueId, facePhoto}
     */
    @PostMapping("/collect")
    public Result<Map<String, Object>> collect(@RequestBody Employee employee) {
        // 必填校验：工号和姓名必须有
        if (StringUtils.isBlank(employee.getEmpNo()) || StringUtils.isBlank(employee.getName())) {
            return Result.fail("工号和姓名不能为空");
        }
        // 工号唯一校验：同一个工号不能采集两次
        long count = employeeService.count(
                Wrappers.<Employee>lambdaQuery().eq(Employee::getEmpNo, employee.getEmpNo()));
        if (count > 0) {
            return Result.fail("工号 " + employee.getEmpNo() + " 已采集过，如需修改请联系管理员");
        }
        // 默认在职
        employee.setStatus(1);
        employeeService.save(employee);

        Map<String, Object> data = new HashMap<>();
        data.put("id", employee.getId());
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
