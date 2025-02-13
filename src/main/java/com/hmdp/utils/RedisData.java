package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

//todo 逻辑过期类
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
