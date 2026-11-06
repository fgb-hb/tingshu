package com.atguigu.tingshu.listener;

import com.atguigu.tingshu.model.album.AlbumInfo;
import io.xzxj.canal.core.annotation.CanalListener;
import io.xzxj.canal.core.listener.EntryListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Set;

/**
 * @author: atguigu
 * @create: 2025-03-22 08:46
 */
@Slf4j
@CanalListener(destination = "tingshuTopic", schemaName = "tingshu_album", tableName = "album_info")
public class AlbumListener implements EntryListener<AlbumInfo> {

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 监听到转机信息表更新
     * @param before 变更前数据
     * @param after 变更后的数据
     * @param fields 变更字段
     */
    @Override
    public void update(AlbumInfo before, AlbumInfo after, Set<String> fields) {
        log.info("[cdc]监听到变更数据，变更前：{}，变更后：{}", before, after);
        Long id = after.getId();
        String redisKey = "album:info:"+id;
        redisTemplate.delete(redisKey);
    }
}
