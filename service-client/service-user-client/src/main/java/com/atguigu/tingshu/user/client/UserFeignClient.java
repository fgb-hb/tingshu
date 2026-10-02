package com.atguigu.tingshu.user.client;

import com.atguigu.tingshu.user.client.impl.UserDegradeFeignClient;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * <p>
 * 用户模块远程调用API接口
 * </p>
 *
 * @author atguigu
 */
@FeignClient(value = "service-user", fallback = UserDegradeFeignClient.class)
public interface UserFeignClient {

}
