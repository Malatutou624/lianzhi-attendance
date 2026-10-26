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

    /** 登录请求参数（写在 Controller 里的内部类，减少文件数） */
    @Data
    public static class LoginDTO {
        private String username;
        private String password;
    }
}
