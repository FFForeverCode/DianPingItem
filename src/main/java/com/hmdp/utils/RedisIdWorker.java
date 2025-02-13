package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {


    /**
     * 初始时间戳
     */
    private static final Long BEGIN_TIMESTAMP = 1739404800L ;
    private static final Long COUNT_BITS = 32L;
    @Autowired
    private RedisTemplate redisTemplate;
    /**
     * 生成全局唯一ID
     * @param keyPrefix 业务名 key前缀
     * @return
     */
    public Long nextId(String keyPrefix){
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        Long nowTimeStamp = now.toEpochSecond(ZoneOffset.UTC);
        Long timeStamp = nowTimeStamp - BEGIN_TIMESTAMP;
        //2.生成序列号
        //2.1获取当前日期
        String dateTime = now.format(DateTimeFormatter.ofPattern("yyy:MM:dd"));
        //2.2自增长
        Long count = redisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + dateTime);
        //3.拼接返回
        return timeStamp << COUNT_BITS | count;
    }

    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2025, 2, 13, 0, 0);
        long epochSecond = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(epochSecond);
    }
}
