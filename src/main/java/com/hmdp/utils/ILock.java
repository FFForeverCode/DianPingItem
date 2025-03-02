package com.hmdp.utils;

/**
 * name: ILock
 * desc: 分布式锁接口
 *
 * @author ForeverRm
 * @version 1.0
 * @date 2025/3/1
 */
public interface ILock {
    /**
     * 获取锁
     * @param timeout 过期时间
     * @return
     */
    boolean tryLock(Long timeout);

    /**
     * 释放锁
     */
    void unLock();
}
