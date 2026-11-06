package com.atguigu.tingshu.common.login;

import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

@Component
@Aspect
@Slf4j
public class GuiGuLoginAspect {
    @Autowired
    private RedisTemplate<Object,Object> redisTemplate;
    @Pointcut("execution(* com.atguigu.tingshu.*.api.*.*(..))")
    public void fgbPointcut() {
    }
    @Around("fgbPointcut() && @annotation(fgb)")
    public Object around(ProceedingJoinPoint joinPoint,Fgb fgb) throws Throwable {
        //进入方法前判断是否需要登录
        //获取请求对象
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        ServletRequestAttributes servletRequestAttributes= (ServletRequestAttributes) requestAttributes;
        HttpServletRequest request = servletRequestAttributes.getRequest();
        //判断是否登录
        //获取请求头中的token
        String token = request.getHeader("token");
        if(StringUtils.isEmpty(token)){
            log.info("请求头中没有token");
        }
        //去redis中查询用户是否存在
        String key = RedisConstant.USER_LOGIN_KEY_PREFIX+token;
        UserInfoVo userInfoVo = (UserInfoVo)redisTemplate.opsForValue().get(key);
        //判断是否需要登录
        //如果需要登录，且用户不存在，抛出异常
        if(fgb.value()==true && userInfoVo == null){
            throw new GuiguException(ResultCodeEnum.LOGIN_AUTH);
        }
        //如果需要登录，且用户存在将用户信息设置到上下文
        if(fgb.value()==true && userInfoVo != null){
            AuthContextHolder.setUserId(userInfoVo.getId());
        }
        Long expire = redisTemplate.getExpire(key, TimeUnit.DAYS);
        if (expire != null && expire <=1 && userInfoVo != null) {
            //自动续期
            redisTemplate.opsForValue().set(key,userInfoVo);
        }
        //如果不需要登录，且用户不存在，继续执行方法
        Object proceed = joinPoint.proceed();
        //方法执行完成后，将用户ID从上下文移除
        AuthContextHolder.removeUserId();
        return proceed;

    }
}
