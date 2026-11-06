package com.atguigu.tingshu.common.cache;

import com.atguigu.tingshu.common.constant.RedisConstant;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface FgbCache {
    /**
     * 存入Redis业务数据以及锁的前缀
     * @return
     */
    String prefix() default "";

    /**
     * 存入Redis缓存业务数据过期时间
     * @return
     */
    long ttl() default RedisConstant.ALBUM_TIMEOUT;

    /**
     * 时间单位
     * @return
     */
    TimeUnit unit() default TimeUnit.SECONDS;
}
