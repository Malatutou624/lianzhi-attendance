package com.lianzhi.attendance.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局配置：Long / long → String
 * JavaScript 的 Number 最大安全整数只有 2^53-1（16 位），
 * MyBatis-Plus ASSIGN_ID 产生的 Snowflake ID 是 19 位 Long，传给前端会丢精度。
 * 这里统一把所有 Long 序列化成 JSON 字符串，前端就能安全解析。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
        };
    }
}
