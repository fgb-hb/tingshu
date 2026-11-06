package com.atguigu.tingshu.search.receiver;

import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.search.service.ItemService;
import com.atguigu.tingshu.search.service.SearchService;
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
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * @author: atguigu
 * @create: 2024-08-09 14:14
 */
@Slf4j
@Component
public class SearchReceiver {

    @Autowired
    private SearchService searchService;
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;
    @Autowired
    private ItemService itemService;


    /**
     * @Exchange(autoDelete="true当最后一个绑定到Exchange上的队列删除后，自动删除该Exchange")
     * @Queue(autoDelete="true没有消费者队列就会自动删除")
     *
     * 监听专辑上架队列，完成专辑上架
     * @param albumId 专辑ID
     * @param message
     * @param channel
     */
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConst.EXCHANGE_ALBUM, durable = "true", autoDelete = "true"),
            value = @Queue(value = MqConst.QUEUE_ALBUM_UPPER, durable = "true", autoDelete = "true"),
            key = MqConst.ROUTING_ALBUM_UPPER
    ))
    public void albumUpper(Long albumId, Message message, Channel channel) {
        if (albumId != null) {
            log.info("[搜索服务]监听到专辑上架消息：{}", albumId);
            searchService.upperAlbum(albumId);
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    /**
     *
     * 监听专辑下架队列，完成专辑下架
     * @param albumId 专辑ID
     * @param message
     * @param channel
     */
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConst.EXCHANGE_ALBUM, durable = "true", autoDelete = "true"),
            value = @Queue(value = MqConst.QUEUE_ALBUM_LOWER, durable = "true", autoDelete = "true"),
            key = MqConst.ROUTING_ALBUM_LOWER
    ))
    public void albumLower(Long albumId, Message message, Channel channel) {
        if (albumId != null) {
            log.info("[搜索服务]监听到专辑下架消息：{}", albumId);
            searchService.lowerAlbum(albumId);
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConst.EXCHANGE_TRACK, durable = "true"),
            value = @Queue(value = MqConst.QUEUE_TRACK_STAT_UPDATE, durable = "true"),
            key ={ MqConst.ROUTING_TRACK_STAT_UPDATE
            }
    ))
    public void updateTrackStat(TrackStatMqVo mqVo,Channel channel, Message message) {
       //1.判断消息是否为空
        if(mqVo != null){
            //1.1构建Redis Key 用setnx命令实现幂等性
            String ksy = RedisConstant.USER_TRACK_REPEAT_STAT_PREFIX+"es:"+mqVo.getBusinessNo();
            Boolean b = redisTemplate.opsForValue().setIfAbsent(ksy, 1, 10, TimeUnit.MINUTES);
            if (b){
                try {
                    //1.2调用服务层方法更新统计
                    itemService.updateTrackStat(mqVo);
                } catch (Exception e) {
                    redisTemplate.delete(ksy);
                    channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
                }
            }
        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
