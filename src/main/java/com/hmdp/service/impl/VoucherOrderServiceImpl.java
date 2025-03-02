package com.hmdp.service.impl;

import com.fasterxml.jackson.databind.ser.Serializers;
import com.hmdp.Context.BaseContext;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.User;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import io.lettuce.core.api.async.RedisTransactionalAsyncCommands;
import org.apache.ibatis.javassist.Loader;
import org.apache.tomcat.util.http.fileupload.FileItemStream;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.instrument.classloading.SimpleLoadTimeWeaver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService service;
    @Autowired
    private VoucherOrderMapper orderMapper;
    @Autowired
    private RedisIdWorker redisIdWorker;

//    @Resource
//    private RedisTemplate redisTemplate;

    //redisson
    @Resource
    private RedissonClient redissonClient;

    /**
     * 抢购优惠券-获取订单
     * todo分布式锁解决集群下的一人一单并发问题
     * todo乐观锁解决库存不足并发问题
     * @param voucherId
     * @return
     */
    @Override
    public Result getOrder(Long voucherId) throws InterruptedException {
        //todo 1.根据优惠券id查询优惠券
        SeckillVoucher voucher = service.getById(voucherId);
        //todo 2.判断活动时间是否有效
//        LocalDateTime begin = voucher.getBeginTime();
//        if(LocalDateTime.now().isAfter(begin)){
//            return Result.fail("活动尚未开始!");
//        }
//        LocalDateTime end = voucher.getEndTime();
//        if(LocalDateTime.now().isBefore(end)){
//            return Result.fail("秒杀已经结束!");
//        }
        //todo 3.判断是否还有库存
        Integer stock = voucher.getStock();
        if (stock <= 0) {
            return Result.fail("库存不足!");
        }
        Long userId = BaseContext.getThreadLocal().getId();//获取用户ID
        //todo 创建锁对象,进行业务操作
        // 1.查询订单-是否一人一单
        // 2.若为一人一单，进行抢购
        // 3.抢购成功后数据库保存订单，反之直接返回
        // 4.若不符合一人一单，抢购失败
        String key = "lock:order:" + userId;
        //redisson获取锁
        RLock lock = redissonClient.getLock(key);
//        SimpleRedisLock lockFactory = SimpleRedisLock.builder()
//                .lockName(key)//注意key的设置很重要,key指定锁的范围
//                .redisTemplate(redisTemplate)
//                .build();

        //todo 尝试获取锁
//        boolean success = lockFactory.tryLock(5L);
        /**
         * 等待时间,锁有效时间
         */
        boolean success = lock.tryLock(1,10, TimeUnit.SECONDS);
        if(!success){
            //获取锁成功
            return Result.fail("一人一单-获取锁失败!");
        }
        //获取锁成功
        try {
            //进行 查找-抢购-修改业务操作
            //todo [代理模式]获取事务注解下的本类的代理对象，代理对象增强事务注解下的方法，实现事务操作
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucher(voucherId);
        }finally {
            //释放锁
            lock.unlock();
        }
    }

    /**
     * 乐观锁-抢购优惠券
     * @param voucherId
     * @return
     */
     @Transactional//todo注意事务注解，为当前类创建代理对象（代理模式），然后增强方法功能
     public  Result createVoucher(Long voucherId) {
        //从订单表中查询该用户，判断该用户是否之前抢购过该优惠券（用户ID+优惠券id）
        List<VoucherOrder> voucherOrder = IfAbsent(BaseContext.getThreadLocal().getId(), voucherId);
        if (voucherOrder != null && !voucherOrder.isEmpty()) {
            return Result.fail("您已抢购过该优惠券!");
        }
        //todo 加乐观锁-抢购优惠券，避免库存不足，更改数据时判断数据前后是否一致
        boolean success = service.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherId).
                gt("stock", 0).update();
        if (!success) {
            return Result.fail("库存不足!");
        }
        //生成订单,将订单保存到数据库中
        VoucherOrder order = new VoucherOrder();
        //订单ID 使用全局订单生成器 生成
        Long orderID = redisIdWorker.nextId("order");
        order.setId(orderID);
        //优惠券id
        order.setVoucherId(voucherId);
        //用户ID
        order.setUserId(BaseContext.getThreadLocal().getId());
        //将订单存储到数据库中
        save(order);
        // return Result.ok();
        return Result.ok(order.getId());
    }

    /**
     * 判断订单表中是否存在该用户抢购指定优惠券
     * @param id 用户id
     * @param voucherId 优惠券id
     * @return 返回订单本身
     */
    public List<VoucherOrder> IfAbsent(Long id, Long voucherId) {
        return orderMapper.IfAbsent(id,voucherId);
    }
}
