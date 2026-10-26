package com.lianzhi.attendance;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 联智·智能考勤系统 启动类
 * @EnableScheduling 开启定时任务支持（Spring Task，第2周用）
 */
@EnableScheduling
@MapperScan("com.lianzhi.attendance.**.mapper")
@SpringBootApplication
public class AttendanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AttendanceApplication.class, args);
        System.out.println("===== 联智考勤系统后端启动成功，端口 8080 =====");
    }
}
