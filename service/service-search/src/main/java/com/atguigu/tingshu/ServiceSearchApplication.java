package com.atguigu.tingshu;

import com.atguigu.tingshu.common.constant.RedisConstant;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)//取消数据源自动配置
@EnableDiscoveryClient
@EnableFeignClients
@EnableAsync
@EnableScheduling
public class ServiceSearchApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(ServiceSearchApplication.class, args);
    }

    @Autowired
    private RedissonClient redissonClient;
    @Override
    public void run(String... args) throws Exception {
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(RedisConstant.ALBUM_BLOOM_FILTER);
        boolean exists = bloomFilter.isExists();
        if (!exists) {
            bloomFilter.tryInit(100000L,0.03);
        }
    }
}
