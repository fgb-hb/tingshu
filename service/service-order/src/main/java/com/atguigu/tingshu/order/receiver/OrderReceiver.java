package com.atguigu.tingshu.order.receiver;

import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.order.service.OrderInfoService;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.PipedReader;

/**
 * @author: atguigu
 * @create: 2024-08-23 14:46
 */
@Slf4j
@Component
public class OrderReceiver {

    @Autowired
    private OrderInfoService orderInfoService;

    /**
     * 监听到延迟关单消息
     *
     * @param orderId 订单ID
     * @param message
     * @param channel
     */
    @SneakyThrows
    @RabbitListener(queues = MqConst.QUEUE_CANCEL_ORDER)
    public void cancelOrder(Long orderId, Message message, Channel channel) {
        if (orderId != null) {
            log.info("[订单服务]监听到延迟关单消息：{}", orderId);
            orderInfoService.cancelOrder(orderId);
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
