package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {
    Result getOrder(Long voucherId) throws InterruptedException;

    List<VoucherOrder> IfAbsent(Long id, Long voucherId);

    void createVoucher(VoucherOrder order);
    Result seckillVoucher(Long voucherId);
}
