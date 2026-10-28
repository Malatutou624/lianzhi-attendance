package com.lianzhi.attendance.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 全局配置：跨域 + 登录拦截器 + 上传文件静态访问
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Value("${lianzhi.upload-dir}")
    private String uploadDir;

    /** 允许前端(5173端口)跨域访问后端(8080端口) */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    /** 登录拦截：只放行纯公开接口，其他都要 token */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",          // 管理端登录
                        "/h5/auth/login",       // H5 登录
                        "/h5/auth/register",    // H5 注册
                        "/uploads/**",          // 已上传照片（<img> 标签不可带 token）
                        "/qrcode/**",           // 二维码图片
                        "/error"
                );
    }

    /** 把 uploads 目录映射成可访问的 URL：/uploads/xxx.jpg */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}
