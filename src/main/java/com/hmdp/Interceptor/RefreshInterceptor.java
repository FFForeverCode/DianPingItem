package com.hmdp.Interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.Context.BaseContext;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 拦截一切路径
 * 刷新token+保存token用户
 * 放行（任何情况下）
 */
@Component
@Slf4j
public class RefreshInterceptor implements HandlerInterceptor {
    @Autowired
    RedisTemplate redisTemplate;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
       log.info("refreshInterceptor 拦截器");
       String token = request.getHeader("authorization");
       if(StrUtil.isBlank(token)){
           return true;
       }
       String key = RedisConstants.LOGIN_USER_KEY+token;
        Map entries = redisTemplate.opsForHash().entries(key);
        if(entries.isEmpty()){
            return true;
        }
        UserDTO userDTO = BeanUtil.fillBeanWithMap(entries,new UserDTO(),false);
        BaseContext.setThreadLocal(userDTO);
        redisTemplate.expire(key,1,TimeUnit.HOURS);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
