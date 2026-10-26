package com.lianzhi.attendance.modules.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lianzhi.attendance.common.Result;
import com.lianzhi.attendance.modules.auth.entity.SysUser;
import com.lianzhi.attendance.modules.auth.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 登录逻辑：
 * 1. 按用户名查数据库
 * 2. 把前端传来的密码做 MD5，和数据库里的比对
 * 3. 成功则生成 UUID token，存进 Redis（2小时过期），返回给前端
 */
@Service
public class AuthService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public Result<Map<String, Object>> login(String username, String password) {
        SysUser user = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (user == null || user.getStatus() != 1) {
            return Result.fail("账号不存在或已停用");
        }
        // 数据库里存的是 MD5(密码)，所以要把输入的密码也 MD5 再比较
        String md5 = DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
        if (!md5.equalsIgnoreCase(user.getPassword())) {
            return Result.fail("密码错误");
        }
        // 生成 token 并存 Redis：key=login:token:xxx, value=userId，2小时有效
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set("login:token:" + token, user.getId().toString(), 2, TimeUnit.HOURS);

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userInfo", user);
        return Result.ok(data);
    }

    public void logout(String token) {
        redisTemplate.delete("login:token:" + token);
    }
}
