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
        // 跨域预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader("Authorization");
        if (token != null && !token.isEmpty()) {
            String userId = redisTemplate.opsForValue().get("login:token:" + token);
            if (userId != null) {
                request.setAttribute("userId", Long.valueOf(userId));
                request.setAttribute("token", token);
                return true;
            }
        }
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"未登录或登录已过期\"}");
        return false;
    }
}
