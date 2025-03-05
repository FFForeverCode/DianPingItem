package com.hmdp.utils;

import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.events.Event;

import javax.annotation.Resource;
import javax.naming.Name;
import javax.xml.crypto.dsig.keyinfo.KeyName;
import java.security.Key;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * name: SimpleRedisLock
 * desc: 简单分布式锁
 *
 * @author ForeverRm
 * @version 1.0
 * @date 2025/3/1
 */
@Data
@Builder
public class SimpleRedisLock implements ILock{

    private String lockName;
    public static final String KEY_PRE = "lock:";
    //UUID作为集群下不同的JVM环境的唯一标识
    public static final String ID_PRE = UUID.randomUUID().toString()+"_";
    private RedisTemplate redisTemplate;
    public SimpleRedisLock(String lockName, RedisTemplate redisTemplate) {
        this.lockName = lockName;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取锁
     * @param timeout 过期时间
     * @return
     */
    @Override
    public boolean tryLock(Long timeout) {
        //todo 锁的唯一标识：JVM唯一标识+JVM下的线程标识（集群下有多个JVM环境）
        String threadId = ID_PRE + Thread.currentThread().getId();
        //todo 1.setNX尝试获取锁
        String lockKey = getKey(lockName);
        //todo 获取锁时，存入线程标识
        Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, threadId, timeout, TimeUnit.SECONDS);
        //todo 2.获取成功 true
        //todo 3.获取失败 false
        //若直接返回 success，为自动拆箱：包装类转换为基本类型，会有空指针异常的风险
        //即：包装类转换为基本数据类型时,本质为包装类调用xxxValue()方法，若包装类为null，会抛出NullException异常
        //处理方法：1.判空处理，2.尽量使用包装类
        return Boolean.TRUE.equals(success);
    }


    //初始化lua脚本文件，类被创建时即被加载，只需加载一次，节约资源
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static{
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        //classPath:去资源文件路径找指定的文件
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);//设置结果类型
    }
    /**
     * 释放锁
     * todo 判断锁唯一标识，解决锁误删问题
     * todo 使用lua脚本实现多条redis命令原子性
     */
    @Override
    public void unLock() {
        //调用lua脚本 实现校验+删除 原子性操作
        String key = getKey(lockName);
        String threadId = ID_PRE+Thread.currentThread().getId();
       redisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(key),//传入keys
                threadId//传入Argv
        );
    }

    public String getKey(String key){
        return KEY_PRE + key;
    }

}
