package com.lianzhi.attendance.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录拦截器：检查请求头 Authorization 里的 token 是否在 Redis 中存在
 * 存在 → 放行，并把 userId 放进 request 供 Controller 使用
 * 不存在 → 返回 401
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String token = request.getHeader("Authorization");
        // 去掉 "Bearer " 前缀（前端统一发送 Authorization: Bearer xxx）
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }
        String uri = request.getRequestURI();

        // 0. H5 员工端 token（路径以 /h5/ 或 /api/h5/ 开头）
        //    Vite 代理会把前端的 /api/h5/** 转成后端的 /h5/**，所以两种都要认
        boolean isH5 = uri.startsWith("/h5/") || uri.startsWith("/api/h5/");
        if (isH5) {
            if (token != null && !token.isEmpty()) {
                String accountId = redisTemplate.opsForValue().get("h5:token:" + token);
                if (accountId != null) {
                    request.setAttribute("h5AccountId", Long.valueOf(accountId));
                    request.setAttribute("h5Token", token);
                    return true;
                }
            }
            return writeUnauthorized(response, "请先登录");
        }

        // 1. 管理端 token（auth / employee / venue / approval / rule / checkin / analysis 等）
        //    后端同时接收带 /api 前缀（直连后端）和不带前缀（经 Vite 代理转来）两种
        //    额外：H5 页面也可能调 checkin/rule/venue/employee 查询接口（只读自己的数据），
        //    所以这些接口在 login:token 找不到时也 fallback 查 h5:token
        boolean isManagement =
                uri.startsWith("/auth/") || uri.startsWith("/api/auth/") ||
                uri.startsWith("/ai/") || uri.startsWith("/api/ai/") ||
                uri.startsWith("/employee/") || uri.startsWith("/api/employee/") ||
                uri.startsWith("/venue/") || uri.startsWith("/api/venue/") ||
                uri.startsWith("/approval/") || uri.startsWith("/api/approval/") ||
                uri.startsWith("/rule/") || uri.startsWith("/api/rule/") ||
                uri.startsWith("/checkin/") || uri.startsWith("/api/checkin/") ||
                uri.startsWith("/analysis/") || uri.startsWith("/api/analysis/");
        if (isManagement) {
            if (token != null && !token.isEmpty()) {
                String userId = redisTemplate.opsForValue().get("login:token:" + token);
                if (userId != null) {
                    request.setAttribute("userId", Long.valueOf(userId));
                    request.setAttribute("token", token);
                    return true;
                }
                // H5 fallback：只读查询接口（employee/venue/rule/checkin）也认 h5 token
                boolean canFallbackH5 =
                        uri.startsWith("/employee/") || uri.startsWith("/api/employee/") ||
                        uri.startsWith("/venue/") || uri.startsWith("/api/venue/") ||
                        uri.startsWith("/rule/") || uri.startsWith("/api/rule/") ||
                        uri.startsWith("/checkin/") || uri.startsWith("/api/checkin/");
                if (canFallbackH5) {
                    String accountId = redisTemplate.opsForValue().get("h5:token:" + token);
                    if (accountId != null) {
                        request.setAttribute("h5AccountId", Long.valueOf(accountId));
                        request.setAttribute("h5Token", token);
                        return true;
                    }
                }
            }
            return writeUnauthorized(response, "未登录或登录已过期");
        }

        // 2. 上传接口 /upload 同时接受管理端 token 或 H5 token（员工打卡采集也需要传照片）
        if (uri.equals("/upload")) {
            if (token != null && !token.isEmpty()) {
                String userId = redisTemplate.opsForValue().get("login:token:" + token);
                if (userId != null) {
                    request.setAttribute("userId", Long.valueOf(userId));
                    request.setAttribute("token", token);
                    return true;
                }
                String accountId = redisTemplate.opsForValue().get("h5:token:" + token);
                if (accountId != null) {
                    request.setAttribute("h5AccountId", Long.valueOf(accountId));
                    request.setAttribute("h5Token", token);
                    return true;
                }
            }
            return writeUnauthorized(response, "未登录或登录已过期");
        }

        // 其他路径（静态资源等）直接放行
        return true;
    }

    private boolean writeUnauthorized(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"" + msg + "\"}");
        return false;
    }
}
