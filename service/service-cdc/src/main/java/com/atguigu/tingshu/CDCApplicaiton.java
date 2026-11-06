package com.atguigu.tingshu;

import io.xzxj.canal.spring.annotation.EnableCanalListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * @author: atguigu
 * @create: 2025-03-21 15:46
 */
@EnableCanalListener
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class CDCApplicaiton {

    public static void main(String[] args) {
        SpringApplication.run(CDCApplicaiton.class, args);
    }
}
