package com.lianzhi.attendance.common;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：任何 Controller 抛出的异常都会被这里接住，
 * 统一返回 {code:500, msg:错误信息}，避免前端看到一堆英文报错堆栈
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        e.printStackTrace(); // 控制台保留完整堆栈，方便自己调试
        return Result.fail("服务器异常：" + e.getMessage());
    }
}
