package com.atguigu.tingshu.listener;

import com.atguigu.tingshu.model.user.UserInfo;
import io.xzxj.canal.core.annotation.CanalListener;
import io.xzxj.canal.core.listener.EntryListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Set;

/**
 * @author: atguigu
 * @create: 2025-03-21 15:47
 */
@Slf4j
@CanalListener(destination = "tingshuTopic", schemaName = "tingshu_user", tableName = "user_info")
public class UserListener implements EntryListener<UserInfo> {

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 监听用户表更新回调方法
     * @param before
     * @param after
     * @param fields
     */
    @Override
    public void update(UserInfo before, UserInfo after, Set<String> fields) {
        log.info("[cdc]监听到变更数据");
        String redisKey = "user:info:"+after.getId();
        redisTemplate.delete(redisKey);
    }
}
