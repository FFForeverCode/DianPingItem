package com.hmdp.config;

import com.hmdp.Interceptor.SessionInterceptor;
import com.hmdp.Interceptor.RefreshInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

@Configuration
@Slf4j
public class WebMvcConfig extends WebMvcConfigurationSupport {
    @Autowired
    private SessionInterceptor sessionInterceptor;

    @Autowired
    private RefreshInterceptor refreshInterceptor;


    public void addInterceptors(InterceptorRegistry interceptorRegistry){
        interceptorRegistry.addInterceptor(sessionInterceptor)
                .excludePathPatterns(
                                    "/user/login",
                                    "/user/code",
                                    "/bolg/hot",
                                    "/shop/**",
                                    "/shop-type/**",
                                    "/voucher/**",
                                    "upload/**"
                )
                .order(1);//顺序
        //该拦截器先执行
        interceptorRegistry.addInterceptor(refreshInterceptor)
                .addPathPatterns("/**")
                .order(0);
    }
}
