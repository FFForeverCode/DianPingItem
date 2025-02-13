package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private RedisTemplate redisTemplate;
    @Override
    public Result queryList() {
        //1.根据shop:type 查询缓存
        //2，命中 返回结果
        //3.没命中 查询数据库
        //4.数据库中没有，返回错误
        //5.数据库中有数据 ，存到缓存中，然后返回结果
        ValueOperations ops = redisTemplate.opsForValue();
        String typeJson = (String)ops.get(RedisConstants.SHOP_TYPE);//查询缓存
        if(StrUtil.isNotBlank(typeJson)){
            return Result.ok(JSONUtil.toList(typeJson, ShopType.class));//返回对象数组
        }
        List<ShopType> list = query().orderByAsc("sort").list();//查询 商铺列表
        if(list == null || list.isEmpty()){
            return Result.fail("没有商铺类型！");
        }
        String jsonStr = JSONUtil.toJsonStr(list);//将商铺列表信息转换为JSON数据
        ops.set(RedisConstants.SHOP_TYPE,jsonStr);//存入redis
        return Result.ok(list);
    }
}
