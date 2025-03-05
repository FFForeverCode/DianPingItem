package com.hmdp.config;

import com.hmdp.utils.RedisConstants;
import net.sf.jsqlparser.util.cnfexpression.CNFConverter;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * name: RedissionConfig
 * desc: Redission配置类
 *
 * @author ForeverRm
 * @version 1.0
 * @date 2025/3/2
 */
@Configuration
public class RedissonConfig {


    /**
     * 创建redisson客户端
     * @return 返回redisson客户端
     */
    @Bean
    public RedissonClient createRedissonClient(){
        //redisson配置
        Config config = new Config();
        //设置地址、密码
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        //返回客户端
        return Redisson.create(config);
    }
}
