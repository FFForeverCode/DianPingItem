package com.hmdp.Exception;

import com.fasterxml.jackson.databind.ser.Serializers;

//基类业务异常
public class BaseException extends RuntimeException {
    public BaseException(){}
    public BaseException(String message) {
        super(message);
    }
}
