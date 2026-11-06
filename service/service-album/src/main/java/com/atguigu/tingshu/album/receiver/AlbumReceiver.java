package com.atguigu.tingshu.album.receiver;

import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.vo.album.TrackStatMqVo;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @author: atguigu
 * @create: 2025-06-09 14:30
 */
@Slf4j
@Component
public class AlbumReceiver {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private TrackInfoService trackInfoService;

    /**
     * 监听到更新声音统计数值MQ消息
     *
     * @param mqVo
     * @param channel
     * @param message
     */
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConst.EXCHANGE_TRACK, durable = "true"),
            value = @Queue(value = MqConst.QUEUE_TRACK_STAT_UPDATE, durable = "true"),
            key = MqConst.ROUTING_TRACK_STAT_UPDATE
    ))
    public void updateTrackStat(TrackStatMqVo mqVo, Channel channel, Message message) {
        if (mqVo != null) {
            log.info("更新声音统计：{}", mqVo);
            //1.先对消费者进行幂等性处理
            //1.1 找出业务消息的标识 作为setnx中key
            String redisKey = RedisConstant.USER_TRACK_REPEAT_STAT_PREFIX + "db:" + mqVo.getBusinessNo();
            //1.2 尝试存入Redis
            Boolean flag = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", 10, TimeUnit.MINUTES);
            if (flag) {
                try {
                    //2.更新声音/专辑统计数值
                    trackInfoService.updateTrackStat(mqVo);
                } catch (Exception e) {
                    redisTemplate.delete(redisKey);
                    //如果异常将无法处理消息发送到死信（异常）交换机->死信队列->消费者处理进入存入消费者异常消息表->人工处理
                    channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
                }
            }
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
