package com.atguigu.tingshu.common.cache;

import com.atguigu.tingshu.common.constant.RedisConstant;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
public class GuiGuCacheAspect {
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RedisTemplate<Object,Object> redisTemplate;
    @Around("@annotation(fgbCache)")
    public Object around(ProceedingJoinPoint joinPoint,FgbCache fgbCache) throws Throwable {
        try {
            //1拼写缓存key
            //1.1获取key的标识，先获取参数有参数就以前缀+参数为为标识，没有参数就以前缀+方法名为标识
            //1.2获取参数
            String key="";
            Object[] args = joinPoint.getArgs();
            if (args!=null && args.length>0) {
                 key=fgbCache.prefix()+args[0].toString();
            }else{
                String methodName = joinPoint.getSignature().getName();
                key=fgbCache.prefix()+methodName;
            }
            //2根据key获取缓存的
            Object o = redisTemplate.opsForValue().get(key);
            if (o != null) {
                //命中缓存，直接返回缓存中的数据
                return o;
            }
            //3没命中缓存就查询数据库
            //3.1获取锁
            String lockKey=key+":"+RedisConstant.CACHE_LOCK_SUFFIX;
            RLock lock = redissonClient.getLock(lockKey);
            boolean b = lock.tryLock(1, TimeUnit.SECONDS);
            if (b){
                try {
                    //获取锁成功，查询数据库
                    Object proceed = joinPoint.proceed();
                    //4.1将数据库查询到的数据缓存到Redis中
                    redisTemplate.opsForValue().set(key,proceed,fgbCache.ttl(),fgbCache.unit());
                    return proceed;
                } finally {
                    lock.unlock();
                }
            }else{
                TimeUnit.SECONDS.sleep(1);
                Object around = this.around(joinPoint, fgbCache);
                return around;
            }
        } catch (Throwable e) {
            log.error("缓存异常",e);
            Object proceed = joinPoint.proceed();
            return proceed;
        }
    }
}