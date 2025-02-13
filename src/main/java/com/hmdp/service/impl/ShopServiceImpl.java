package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.datatype.jsr310.deser.key.LocalDateKeyDeserializer;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheUtil;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.time.LocalDateTime.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private CacheUtil cacheUtil;
    @Resource
    private RedisTemplate redisTemplate;


    @Override
    public Result queryById(Long id) throws InterruptedException {
        //todo 解决缓存穿透函数查询shop
        //Shop shop = queryWithPassThrough(id);


        //todo 解决缓存击穿：热点key,高并发问题
        //使用互斥锁解决
        Shop shop = queryWithMutex(id);
        if(shop == null){
            return Result.fail("无该商户!");
        }
        return Result.ok(shop);
    }

    //todo 使用工具类 设置null解决缓存穿透问题 的查询函数
    public Shop queryWithPassThrough(Long id){
        String keyPrefix = RedisConstants.CACHE_SHOP_KEY;
        return cacheUtil.queryWithPassThrough(keyPrefix,id,Shop.class,
                this::getById, 30L,TimeUnit.SECONDS);
    }
    /**
     * todo 互斥锁解决缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) throws InterruptedException {
        //1.从redis查询缓存
        //2.判断是否命中
        //3.命中，直接返回结果
        ValueOperations ops = redisTemplate.opsForValue();
        String shopJson = (String) ops.get(RedisConstants.CACHE_SHOP_KEY+id);
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if(shopJson != null){
            return null;
        }
        //todo 未命中 查询数据库 实现缓存重建
        //todo 获取互斥锁
        Boolean lock = tryLock(String.valueOf(id));
        Shop shop = null;
        //todo 如果获取互斥锁成功，则进行重建
        if(lock){
            //获取互斥锁，查询数据库，并将数据写入redis
            shop = getById(id);//Mybatis-Plus增强功能
            if(shop == null) return null;
            //数据库存在，先将数据写入redis，然后再将结果返回
            String jsonStr = JSONUtil.toJsonStr(shop);//手动转为JSON数据
            redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,jsonStr);
            //释放互斥锁
            unLock(String.valueOf(id));
            return shop;
        }
        //todo 获取失败，休眠
        Thread.sleep(100);
        //todo 休眠之后，再次查询缓存 直至缓存已重建
        return queryWithMutex(id);
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

    /**
     * 根据id修改店铺信息
     * todo保证数据库与redis缓存数据一致性
     *      先修改数据库，再删除redis缓存
     *      使用事务注解，使得操作数据库与缓存为整个事务，保证事务的原子性
     * @param shop
     */
    @Override
    public void updateShopById(Shop shop) {
        //1.先操作数据库
        updateById(shop);
        //2.再删除缓存 缓存：key:shopKey + id   value:json
        ValueOperations ops = redisTemplate.opsForValue();
        String key = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        String json = (String)ops.get(key);
        if (StrUtil.isNotBlank(json)) {
            redisTemplate.delete(key);
        }
    }
    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期处理缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id){
        //1.查询缓存
        ValueOperations ops = redisTemplate.opsForValue();
        String key = RedisConstants.CACHE_SHOP_KEY+id;
        String  json = (String)ops.get(key);
        if(json == null){
            //1.1没有命中 直接返回结果
            return null;
        }
        //1.2命中，转为po,获取内部shop类、逻辑过期时间
        RedisData redisData = JSONUtil.toBean(json,RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        JSONObject data = (JSONObject) redisData.getData();//todo 获取内部Object-data
        Shop shop = JSONUtil.toBean(data, Shop.class);//todo 将json 转为实体类
        //2.判断是否逻辑过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //2.1未过期,返回内部shop类结果
            return shop;
        }

        //2.2过期 缓存重建
        //获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        //获取成功
        if(isLock){
            try {
                //todo 成功 开启独立线程 业务重建
                CACHE_REBUILD_EXECUTOR.submit(()->{
                    saveShopRedis(id,20L);
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }finally {
                unLock(lockKey);//释放锁
            }
        }
        //获取失败，直接返回过期结果
        return shop;


    }
    /**
     * redis存储已设置逻辑过期的店铺数据
     * 缓存重建
     * @param id
     * @param expire
     */
    //将shop数据(已设置逻辑过期时间)保存到redis中（热点数据）
    public void saveShopRedis(Long id,Long expire){
        //1.查询店铺数据
        Shop shop = getById(id);
        //2.封装过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(now().plusSeconds(expire));
        //3.写入redis
        ValueOperations ops = redisTemplate.opsForValue();
        String key = RedisConstants.CACHE_SHOP_KEY+id;
        String dataJson = JSONUtil.toJsonStr(redisData);
        ops.set(key,dataJson);
    }
}
