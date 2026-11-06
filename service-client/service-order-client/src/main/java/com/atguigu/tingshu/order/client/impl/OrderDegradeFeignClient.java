package com.atguigu.tingshu.order.client.impl;


import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.order.client.OrderFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
@Slf4j
@Component
public class OrderDegradeFeignClient implements OrderFeignClient {

    @Override
    public Result<OrderInfo> getOrderInfo(String orderNo) {
        //1.返回默认值
        log.info("根据订单号查询订单信息失败，订单号：{}", orderNo);
        return Result.fail();
    }


    @Override
    public Result orderPaySuccess(String orderNo) {
        log.error("[订单服务]提供远程调用orderPaySuccess方法执行服务降级");
        return null;
    }
}
