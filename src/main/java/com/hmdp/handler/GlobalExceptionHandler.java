package com.hmdp.handler;

import com.fasterxml.jackson.databind.ser.Serializers;
import com.hmdp.Exception.BaseException;
import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

//全局异常处理器
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler
    public Result ExceptionHandle(BaseException baseException){
        log.error("异常:{}",baseException.getMessage());
        return Result.fail(baseException.getMessage());
    }
}
