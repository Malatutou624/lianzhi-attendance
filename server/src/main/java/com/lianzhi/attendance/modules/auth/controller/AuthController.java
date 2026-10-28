package com.lianzhi.attendance.modules.auth.controller;

import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.auth.entity.SysUser;
import com.lianzhi.attendance.modules.auth.mapper.SysUserMapper;
import com.lianzhi.attendance.modules.auth.service.AuthService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 登录相关接口
 * POST /auth/login   登录（不需要token）
 * GET  /auth/info    获取当前登录用户信息
 * POST /auth/logout  退出登录
 * GET  /auth/profile 获取当前管理员个人资料（含真实姓名/手机/邮箱/头像）
 * PUT  /auth/profile 更新当前管理员个人资料
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginDTO dto) {
        if (dto.getUsername() == null || dto.getPassword() == null) {
            return Result.fail("用户名和密码不能为空");
        }
        return authService.login(dto.getUsername(), dto.getPassword());
    }

    @GetMapping("/info")
    public Result<SysUser> info(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId"); // 拦截器放进去的
        return Result.ok(sysUserMapper.selectById(userId));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = (String) request.getAttribute("token");
        authService.logout(token);
        return Result.ok();
    }

    /** 获取当前管理员个人资料 */
    @GetMapping("/profile")
    public Result<SysUser> profile(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        SysUser u = sysUserMapper.selectById(userId);
        if (u != null) u.setPassword(null); // 脱敏
        return Result.ok(u);
    }

    /** 更新当前管理员个人资料（realName/phone/email/avatar） */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody SysUser input, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        SysUser u = sysUserMapper.selectById(userId);
        if (u == null) return Result.fail("用户不存在");
        if (input.getRealName() != null) u.setRealName(input.getRealName());
        if (input.getPhone() != null) u.setPhone(input.getPhone());
        if (input.getEmail() != null) u.setEmail(input.getEmail());
        if (input.getAvatar() != null) u.setAvatar(input.getAvatar());
        sysUserMapper.updateById(u);
        return Result.ok();
    }

    /** 登录请求参数（写在 Controller 里的内部类，减少文件数） */
    @Data
    public static class LoginDTO {
        private String username;
        private String password;
    }
}
