package com.atguigu.tingshu.common.util;

/**
 * 获取当前用户信息帮助类
 */
public class AuthContextHolder {

    private static ThreadLocal<Long> userId = new ThreadLocal<Long>();

    public static void setUserId(Long _userId) {
        userId.set(_userId);
    }

    public static Long getUserId() {
        //先硬编码，后续再从token中获取用户id
//        return 1L;
        return userId.get();
    }

    public static void removeUserId() {
        userId.remove();
    }

}
