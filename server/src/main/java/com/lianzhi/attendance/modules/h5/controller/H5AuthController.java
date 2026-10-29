package com.lianzhi.attendance.modules.h5.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.employee.entity.Employee;
import com.lianzhi.attendance.modules.employee.mapper.EmployeeMapper;
import com.lianzhi.attendance.modules.h5.entity.H5Account;
import com.lianzhi.attendance.modules.h5.mapper.H5AccountMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * H5 员工端账号接口
 * POST /h5/auth/register  注册
 * POST /h5/auth/login     账号密码登录
 * POST /h5/auth/wx-login  微信小程序登录（code换openid）
 * GET  /h5/auth/info     获取当前账号信息（需token）
 * POST /h5/auth/logout   退出
 */
@Slf4j
@RestController
@RequestMapping("/h5/auth")
public class H5AuthController {

    @Autowired private H5AccountMapper accountMapper;
    @Autowired private EmployeeMapper employeeMapper;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private ObjectMapper objectMapper;

    @Value("${lianzhi.wx.appid}") private String wxAppid;
    @Value("${lianzhi.wx.secret}") private String wxSecret;

    private static final String TOKEN_PREFIX = "h5:token:";

    /** 注册 */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody RegisterDTO dto) {
        if (dto.username == null || dto.username.length() < 3) return Result.fail("账号至少3位");
        if (dto.password == null || dto.password.length() < 6) return Result.fail("密码至少6位");
        // 账号唯一
        long cnt = accountMapper.selectCount(Wrappers.<H5Account>lambdaQuery()
                .eq(H5Account::getUsername, dto.username));
        if (cnt > 0) return Result.fail("账号已存在");

        H5Account acc = new H5Account();
        acc.setUsername(dto.username);
        acc.setPassword(DigestUtils.md5DigestAsHex(dto.password.getBytes(StandardCharsets.UTF_8)));
        acc.setStatus(1);
        accountMapper.insert(acc);

        return Result.ok(loginInternal(acc));
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody RegisterDTO dto) {
        H5Account acc = accountMapper.selectOne(Wrappers.<H5Account>lambdaQuery()
                .eq(H5Account::getUsername, dto.username));
        if (acc == null || acc.getStatus() != 1) return Result.fail("账号不存在或已停用");
        String md5 = DigestUtils.md5DigestAsHex(dto.password.getBytes(StandardCharsets.UTF_8));
        if (!md5.equalsIgnoreCase(acc.getPassword())) return Result.fail("密码错误");

        return Result.ok(loginInternal(acc));
    }

    /**
     * 微信小程序登录
     * 小程序端调 wx.login() → 拿到 code → 调此接口 → 后端调微信 code2session 换 openid → 自动绑定/创建账号
     * 请求体: { "code": "xxx" }
     */
    @PostMapping("/wx-login")
    public Result<Map<String, Object>> wxLogin(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isEmpty()) return Result.fail("缺少 code 参数");

        try {
            // 1. 调微信 code2session 接口
            String url = "https://api.weixin.qq.com/sns/jscode2session"
                    + "?appid=" + wxAppid
                    + "&secret=" + wxSecret
                    + "&js_code=" + code
                    + "&grant_type=authorization_code";
            RestTemplate restTemplate = new RestTemplate();
            String resp = restTemplate.getForObject(url, String.class);
            JsonNode node = objectMapper.readTree(resp);

            if (node.has("errcode")) {
                log.warn("微信 code2session 失败: {}", resp);
                return Result.fail("微信登录失败: " + node.path("errmsg").asText());
            }
            String openid = node.path("openid").asText();
            String unionid = node.has("unionid") ? node.path("unionid").asText() : null;
            log.info("微信小程序登录 openid={}", openid);

            // 2. 按 openid 找已有账号
            H5Account acc = accountMapper.selectOne(Wrappers.<H5Account>lambdaQuery()
                    .eq(H5Account::getOpenid, openid));

            if (acc == null) {
                // 3. 自动创建新账号（首次登录）
                acc = new H5Account();
                acc.setUsername("wx_" + openid.substring(0, Math.min(8, openid.length())));
                acc.setPassword(DigestUtils.md5DigestAsHex(("wx_" + openid).getBytes(StandardCharsets.UTF_8)));
                acc.setOpenid(openid);
                acc.setStatus(1);
                accountMapper.insert(acc);
                log.info("新微信用户注册: accountId={}, openid={}", acc.getId(), openid);
            } else {
                if (acc.getStatus() != 1) return Result.fail("账号已停用");
            }

            return Result.ok(loginInternal(acc));
        } catch (Exception e) {
            log.error("微信登录异常", e);
            return Result.fail("微信登录服务异常: " + e.getMessage());
        }
    }

    /** 获取当前账号信息（含绑定的员工信息） */
    @GetMapping("/info")
    public Result<Map<String, Object>> info(HttpServletRequest request) {
        Long accountId = (Long) request.getAttribute("h5AccountId");
        if (accountId == null) return Result.fail("未登录");
        H5Account acc = accountMapper.selectById(accountId);
        if (acc == null) return Result.fail("账号不存在");

        Map<String, Object> data = new HashMap<>();
        data.put("accountId", acc.getId());
        data.put("username", acc.getUsername());
        data.put("empId", acc.getEmpId());
        if (acc.getEmpId() != null) {
            Employee emp = employeeMapper.selectById(acc.getEmpId());
            if (emp != null) {
                Map<String, Object> empInfo = new HashMap<>();
                empInfo.put("id", emp.getId());
                empInfo.put("empNo", emp.getEmpNo());
                empInfo.put("name", emp.getName());
                empInfo.put("dept", emp.getDept());
                empInfo.put("position", emp.getPosition());
                empInfo.put("facePhoto", emp.getFacePhoto());
                data.put("employee", empInfo);
            }
        }
        return Result.ok(data);
    }

    /** 退出 */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = (String) request.getAttribute("h5Token");
        if (token != null) redisTemplate.delete(TOKEN_PREFIX + token);
        return Result.ok();
    }

    /** 内部：生成 token + 返回账号信息 */
    private Map<String, Object> loginInternal(H5Account acc) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(TOKEN_PREFIX + token, acc.getId().toString(), 7, TimeUnit.DAYS);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("accountId", acc.getId());
        data.put("username", acc.getUsername());
        data.put("empId", acc.getEmpId());
        data.put("bound", acc.getEmpId() != null);
        return data;
    }

    @Data
    public static class RegisterDTO {
        private String username;
        private String password;
    }
}
