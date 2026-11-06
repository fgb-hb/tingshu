package com.atguigu.tingshu.order.client;

import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.order.client.impl.OrderDegradeFeignClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * <p>
 * 订单模块远程调用API接口
 * </p>
 *
 * @author atguigu
 */
@FeignClient(value = "service-order", path = "api/order", fallback = OrderDegradeFeignClient.class)
public interface OrderFeignClient {
    /**
     * 根据订单号查询订单信息
     * @param orderNo
     * @return
     */
    @GetMapping("/orderInfo/getOrderInfo/{orderNo}")
    public Result<OrderInfo> getOrderInfo(@PathVariable String orderNo);
    /**
     * 用户付款成功后，处理订单相关业务
     * @param orderNo
     * @return
     */
    @GetMapping("/orderInfo/orderPaySuccess/{orderNo}")
    public Result orderPaySuccess(@PathVariable String orderNo);
}
