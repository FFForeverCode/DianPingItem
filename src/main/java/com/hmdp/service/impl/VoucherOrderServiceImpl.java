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
import org.apache.tomcat.util.http.fileupload.FileItemStream;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
    @Override
    public Result getOrder(Long voucherId) {
        SeckillVoucher voucher = service.getById(voucherId);
//        LocalDateTime begin = voucher.getBeginTime();
//        if(LocalDateTime.now().isAfter(begin)){
//            return Result.fail("活动尚未开始!");
//        }
//        LocalDateTime end = voucher.getEndTime();
//        if(LocalDateTime.now().isBefore(end)){
//            return Result.fail("秒杀已经结束!");
//        }
        Integer stock = voucher.getStock();
        if(stock<=0){
            return Result.fail("库存不足!");
        }
        Long userId = BaseContext.getThreadLocal().getId();
        synchronized (userId.toString().intern()) {//为每个用户设置单独的锁
            //todo 由于事务注解是生成本类的代理对象，执行增强方法来实现的，并非执行原本的方法
            //todo 因此执行事务注解的方法，需要获取当前类的代理对象，然后执行代理对象增强的方法。
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();//todo 获取代理对象
            return proxy.createVoucher(voucherId);//todo 执行事务注解的增强方法
        }
    }

    /**
     * 为当前用户添加悲观锁-解决并发问题
     * 不同的用户不会锁定
     * 悲观锁使得线程串行执行-变为单线程
     * @param voucherId
     * @return
     */
    @Transactional//todo注意事务注解，为当前类创建代理对象，然后增强方法功能
     public  Result createVoucher(Long voucherId) {
        //todo限定单个用户抢购指定优惠券的数量
        //从订单表中查询该用户，判断该用户是否之前抢购过该优惠券（用户ID+优惠券id）

        List<VoucherOrder> voucherOrder = IfAbsent(BaseContext.getThreadLocal().getId(), voucherId);
        if (voucherOrder != null && !voucherOrder.isEmpty()) {
            return Result.fail("您已抢购过该优惠券!");
        }
        //todo 加乐观锁  更改数据时判断数据前后是否一致
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
