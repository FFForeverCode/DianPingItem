package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.impl.DurationConverter;
import com.hmdp.Context.BaseContext;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.TypeReferenceAdjustment;
import org.aspectj.weaver.ast.Var;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService service;
    @Autowired
    private VoucherOrderMapper orderMapper;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Resource
    RedisTemplate redisTemplate;

//    @Resource
//    private RedisTemplate redisTemplate;

    //redisson
    @Resource
    private RedissonClient redissonClient;

    /**
     * 抢购优惠券-获取订单
     * todo分布式锁解决集群下的一人一单并发问题
     * todo乐观锁解决库存不足并发问题
     *
     * @param voucherId
     * @return
     */
    @Override
    @Deprecated
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
        boolean success = lock.tryLock(1, 10, TimeUnit.SECONDS);
        if (!success) {
            //获取锁成功
            return Result.fail("一人一单-获取锁失败!");
        }
        //获取锁成功
        try {
            //进行 查找-抢购-修改业务操作
            //todo [代理模式]获取事务注解下的本类的代理对象，代理对象增强事务注解下的方法，实现事务操作
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            //return proxy.createVoucher();
        } finally {
            //释放锁
            lock.unlock();
        }
        return null;

    }

    /**
     * 更新数据库中的订单数据、库存数据
     * @param order
     * @return
     */
    @Transactional//todo注意事务注解，为当前类创建代理对象（代理模式），然后增强方法功能
    public void createVoucher(VoucherOrder order) {
        Long userId = order.getUserId();
        //1.查询订单数量
        int count = query().
                eq("user_id", userId).
                eq("voucher_id", order.getId()).
                count();
        if (count > 0) {
            log.error("已经购买过一次了");
            return;
        }
        //2.扣减库存
        boolean success = service.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", order).gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足");
        }
        //3.创建订单
        save(order);
    }

    /**
     * 判断订单表中是否存在该用户抢购指定优惠券
     *
     * @param id        用户id
     * @param voucherId 优惠券id
     * @return 返回订单本身
     */
    public List<VoucherOrder> IfAbsent(Long id, Long voucherId) {
        return orderMapper.IfAbsent(id, voucherId);
    }


    //初始化lua脚本文件，类被创建时即被加载，只需加载一次，节约资源
    private static final DefaultRedisScript<Long> VOUCHER_SCRIPT;

    static {
        VOUCHER_SCRIPT = new DefaultRedisScript<>();
        //classPath:去资源文件路径找指定的文件
        VOUCHER_SCRIPT.setLocation(new ClassPathResource("Voucher.lua"));
        VOUCHER_SCRIPT.setResultType(Long.class);//设置结果类型
    }

    /**
     * 阻塞队列
     */
    //private static BlockingQueue<VoucherOrder> queue = new ArrayBlockingQueue<>(1024 * 1024);

    /**
     * 单线程线程池
     */
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();

    @PostConstruct//todo 在当前类初始化完毕后就执行该方法
    public void init() {
        //2.执行更新数据库操作任务-异步操作
        EXECUTOR_SERVICE.submit(new updateOrder());
    }

    /**
     * 线程任务
     * 抢购后更新数据库订单信息
     */
    private class updateOrder implements Runnable {

        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true) {

                try {
                    //1.获取消息队列中的订单信息
                    // XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.order >
                    StreamOperations opsStream = redisTemplate.opsForStream();
                    List<MapRecord<String,Object,Object>> read = opsStream.read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //2.判断获取消息是否成功
                    //读取消息为空,则说明没有订单信息，继续下一次循环
                    if(read == null || read.isEmpty()){
                        continue;
                    }
                    //3.获取成功，可以下单
                    //3.1解析信息
                    MapRecord<String, Object, Object> message = read.get(0);
                    Map<Object,Object>values = message.getValue();
                    //todo 使用BeanUtil.fillBeanWithMap 将map对象转换为entity bean对象
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    handleVoucherOrder(voucherOrder);
                    //4.ACK确认消息执行完毕，从pendingList中删除处理的消息
                    // SACK stream.orders g1 id
                    redisTemplate.opsForStream().acknowledge(queueName,"g1",message.getId());
                } catch (InterruptedException e) {
                    //抛出异常，消息还没被sack确认
                    log.error("创建订单失败,异常");
                    try {
                        //执行 处理pending-list待处理消息队列
                        handlePendingList();
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
        private void handlePendingList() throws InterruptedException {
            while (true) {
                try {
                    //1.获取pending-list中的订单信息
                    // XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.order 0
                    StreamOperations opsStream = redisTemplate.opsForStream();
                    List<MapRecord<String,Object,Object>> read = opsStream.read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //2.判断获取消息是否成功
                    //读取pending-list为空,则说明没有订单信息
                    if(read == null || read.isEmpty()){
                        //说明pending-list没有消息，结束循环
                        break;
                    }
                    //3.获取成功，可以下单
                    //3.1解析信息
                    MapRecord<String, Object, Object> message = read.get(0);
                    Map<Object,Object>values = message.getValue();
                    //todo 使用BeanUtil.fillBeanWithMap 将map对象转换为entity bean对象
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    handleVoucherOrder(voucherOrder);
                    //4.ACK确认消息执行完毕，从pendingList中删除处理的消息
                    // SACK stream.orders g1 id
                    redisTemplate.opsForStream().acknowledge(queueName,"g1",message.getId());
                } catch (InterruptedException e) {
                    //抛出异常，消息还没被sack确认
                    log.error("处理pending-list失败,异常");
                    Thread.sleep(200);
                }
            }
        }
    }

        /**
         * 父线程的代理对象
         */
        private IVoucherOrderService proxy;
        /**
         * 处理阻塞队列中的信息，进行数据库数据更新
         * @param voucherOrder
         * @throws InterruptedException
         */
        private void handleVoucherOrder(VoucherOrder voucherOrder) throws InterruptedException {
            String key = "lock:order:" + BaseContext.getThreadLocal().getId();
            //redisson获取锁
            RLock lock = redissonClient.getLock(key);

            /**
             * 等待时间,锁有效时间
             */
            boolean success = lock.tryLock(1, 10, TimeUnit.SECONDS);
            if (!success) {
                log.info("不允许重复下单");
            }
            //获取锁成功
            try {
                //todo [代理模式]获取事务注解下的本类的代理对象，代理对象增强事务注解下的方法，实现事务操作
                //todo 注意子线程中无法得到父线程中的代理对象
                proxy.createVoucher(voucherOrder);
            } finally {
                //释放锁
                lock.unlock();
            }
        }

        /**
         * 1.查询是否符合抢购资格
         *
         * @param voucherId
         * @return
         */
        @Override
        public Result seckillVoucher(Long voucherId) {
            //订单ID 使用全局订单生成器 生成
            long orderId = redisIdWorker.nextId("order");
            //1.执行lua脚本
            Long status = (Long) redisTemplate.execute(
                    VOUCHER_SCRIPT,
                    Collections.emptyList(),
                    voucherId.toString(),
                    BaseContext.getThreadLocal().getId().toString(),
                    String.valueOf(orderId)
            );
            int r = status.intValue();
            //2.判断lua脚本的结果
            if (r != 0) {
                return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
            }

            //已弃用，stream消息队列实现
//            //生成订单
//            //生成订单
//            VoucherOrder order = new VoucherOrder();
//            order.setId(orderId);
//            //优惠券id
//            order.setVoucherId(voucherId);
//            //用户ID
//            order.setUserId(BaseContext.getThreadLocal().getId());
////            todo 3.有资格下单，将下单信息保存到阻塞队列中去
////            queue.add(order);


            //todo 设置父线程的代理对象，在子线程中执行事务操作
            proxy = (IVoucherOrderService) AopContext.currentProxy();
            //返回订单id
            return Result.ok(orderId);
        }
}
