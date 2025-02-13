package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.Exception.UserNullException;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.*;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.statement.select.KSQLJoinWindow;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import javax.management.StringValueExp;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private UserMapper userMapper;
    /**
     * 发送验证码
     * @param phone 手机号
     * @param session 保存验证码的session
     * @return 返回结果
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //验证手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail(ExceptionConstants.PHONE_ERROR);
        }
        //生产验证码
        //使用工具类生成6字符大小的验证码
        String code = RandomUtil.randomNumbers(NumberConstants.DEFAULT_CODE_NUM);
        //使用session保存验证码、手机号
        //弃用session，使用redis保存验证信息，实现数据共享
//        session.setAttribute(UserConstants.CODE,code);
//        session.setAttribute(UserConstants.PHONE,phone);
        ValueOperations ops = redisTemplate.opsForValue();
        ops.set(phone, code,RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);//设置有效期为60s
        //发送验证码
        log.debug("发送验证码成功!,{}",code);
        return Result.ok();
    }

    /**
     * 验证登录
     * @param loginForm 用户登录数据封装类
     * @param session session
     * @return 返回结果
     */
    @Override
    public Result verifyLogin(LoginFormDTO loginForm, HttpSession session) {
        //验证手机号和code
            //验证失败，返回result.fail
//        String code = (String)session.getAttribute(UserConstants.CODE);//获取生成验证码时保存在Session中的code
//        String phone = (String)session.getAttribute(UserConstants.PHONE);//验证手机号
//
//        if(phone == null || !phone.equals(loginForm.getPhone())){
//            return Result.fail(ExceptionConstants.PHONE_ERROR);
//        }
//        if(code==null || !code.equals(loginForm.getCode())){
//            return Result.fail(ExceptionConstants.CODE_ERROR);
//        }
        String phone = loginForm.getPhone();
        String code = (String)redisTemplate.opsForValue().get(phone);
        if(code == null || !code.equals(loginForm.getCode())){
            return Result.fail(ExceptionConstants.CODE_ERROR);
        }
        //验证成功后，根据手机号查询用户
        //select* from tb_user where phone == #{phone}
        User user = userMapper.selectByPhone(phone);
        //新用户，则注册并保存用户信息
        if (user == null) {
            user = createNewUser(phone);
            userMapper.insert(user);
        }
        //保存用户信息到session中
//        session.setAttribute(UserConstants.USER, userDTO);

        //生成token作为key保存到redis中，并返回给前端
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO =new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        //将UserDto转为Map
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
        userMap.put("id", String.valueOf(userDTO.getId()));//将Long类型 转为 字符串
        redisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY+token,userMap);//将数据存储到redis中
        return Result.ok(token);
    }



    /**
     * 创建用户
     * @param phone
     * @return
     */
    private User createNewUser(String phone){
        User user = new User();
        user.setPhone(phone);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX +
                RandomUtil.randomString(NumberConstants.DEFAULT_NAME_NUM));

        return user;
    }
}
