package com.hmdp.Context;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;

public class BaseContext {
    private static final ThreadLocal<UserDTO>threadLocal = new InheritableThreadLocal<>();
    public static void setThreadLocal(UserDTO user){
        threadLocal.set(user);
    }
    public static UserDTO getThreadLocal(){
        return threadLocal.get();
    }
    public static void removeThreadLocal(){
        threadLocal.remove();
    }
}
