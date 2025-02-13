package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    ShopServiceImpl shopService;
    @Test
    public void testSave(){
        shopService.saveShopRedis(1L, 100L);
    }
    @Test
    public void testPassThroughUtil(){
        Shop shop = shopService.queryWithPassThrough(0L);
        System.out.println(shop);
    }

}
