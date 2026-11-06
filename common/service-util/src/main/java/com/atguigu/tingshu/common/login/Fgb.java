package com.atguigu.tingshu.common.login;

import java.lang.annotation.*;
//用于方法注解，判断是否需要登录
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Fgb {
    boolean value() default true;
}
