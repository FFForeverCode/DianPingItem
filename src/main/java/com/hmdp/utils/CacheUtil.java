package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.sql.Time;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

//缓存工具类 实现常见的缓存功能
@Component
public class CacheUtil {
    private final RedisTemplate redisTemplate;

    //根据构造函数注入
    public CacheUtil(RedisTemplate redisTemplate){
        this.redisTemplate = redisTemplate;
    }
    //存储数据 设置有效期
    public void set(String key, Object value, Long time, TimeUnit unit){
        String Json = JSONUtil.toJsonStr(value);
        redisTemplate.opsForValue().set(key,Json,time,unit);
    }

    /**
     * 保存数据并设置逻辑时间
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void setLogical(String key, Object value, Long time, TimeUnit timeUnit){
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        redisData.setData(value);
        String json = JSONUtil.toJsonStr(redisData);
        redisTemplate.opsForValue().set(key,json);
    }

    /**
     * todo 设置null解决缓存穿透问题 的查询函数
     * @param keyPrefix key前缀
     * @param id 查询键
     * @param type 查询返回的实体类型
     * @param dbFallBack DAO层方法
     * @param time 有效期
     * @param unit 时间单位
     * @return 返回查询到的实体类
     * @param <R> 实体类类型
     * @param <ID> id类型
     */
    public  <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function<ID,R>dbFallBack,Long time,TimeUnit unit){
        //1.从redis查询缓存
        //2.判断是否命中
        //3.命中，直接返回结果
        ValueOperations ops = redisTemplate.opsForValue();
        String key = keyPrefix + id;
        String Json = (String) ops.get(key);
        if (StrUtil.isNotBlank(Json)) {
            return JSONUtil.toBean(Json,type);
        }
        if(Json != null&&Json.equals("")){
            //todo 判断是否命中空值
            return null;
        }
        //4.未命中 查询数据库
        //todo 参数传入的方法
        R r = dbFallBack.apply(id);//todo 重点
        //5.数据库不存在，返回错误
        if( r == null){
            //todo 缓存存储空值，解决缓存穿透问题
            redisTemplate.opsForValue().set(key,"",
                    RedisConstants.CACHE_NULL_TTL,
                    TimeUnit.MINUTES);
            return null;
        }
        //6.数据库存在，先将数据写入redis，然后再将结果返回
        String jsonStr = JSONUtil.toJsonStr(r);//手动转为JSON数据
        //todo 添加超时剔除，为数据一致性兜底
        ops.set(key,jsonStr,time, unit);
        return r;
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    /**
     * todo 互斥锁解决缓存击穿工具方法
     * @param id
     * @return
     */
    public <R,T> R queryWithMutex(T id,String keyPrefix,Class<R>type
    ,Function<T,R>function) throws InterruptedException {
        //1.从redis查询缓存
        //2.判断是否命中
        //3.命中，直接返回结果
        ValueOperations ops = redisTemplate.opsForValue();
        String key = keyPrefix + id;
        String json = (String) ops.get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json,type);
        }
        if(json != null){
            return null;
        }
        //todo 未命中 查询数据库 实现缓存重建
        //todo 获取互斥锁
        Boolean lock = tryLock(String.valueOf(id));
        R r = null;
        //todo 如果获取互斥锁成功，则进行重建
        if(lock){
            //获取互斥锁，查询数据库，并将数据写入redis
            r = function.apply(id);
            if(r == null) return null;
            //数据库存在，先将数据写入redis，然后再将结果返回
            String jsonStr = JSONUtil.toJsonStr(r);//手动转为JSON数据
            redisTemplate.opsForValue().set(key,jsonStr);
            //释放互斥锁
            unLock(String.valueOf(id));
            return r;
        }
        //todo 获取失败，休眠
        Thread.sleep(100);
        //todo 休眠之后，再次查询缓存 直至缓存已重建
        return queryWithMutex(id,keyPrefix,type,function);
    }
    //todo 实现生成互斥锁
    private Boolean tryLock(String key){
        //实现互斥锁，第一个调用该方法存入redis的线程第一个成功存入，返回true
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //todo 实现删除互斥锁
    private void unLock(String key){
        redisTemplate.delete(key);
    }


}
