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

    /** 登录拦截：除了登录接口、H5接口、上传文件、二维码图片外，都要带 token */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",   // 登录接口
                        "/h5/**",        // H5 员工端接口（员工没有管理端账号）
                        "/upload",       // 上传照片（H5 打卡页/采集页拍照上传，无 token）
                        "/uploads/**",   // 上传的照片
                        "/qrcode/**",    // 二维码图片（<img>标签无法带请求头）
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
