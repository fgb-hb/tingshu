# 听书有声书微服务项目
基于 Spring Cloud Alibaba 微服务架构的有声书小程序后端，配套 uni-app + Vue3 + TS 微信小程序，Docker容器化部署。

## 技术栈
Java17、SpringBoot3、SpringCloudAlibaba(Nacos / OpenFeign / Sentinel)、MyBatis-Plus
MySQL、Redis(Redisson)、RabbitMQ、Elasticsearch+IK分词、MongoDB
Seata、XXL-JOB、Canal、MinIO、微信支付V3、腾讯云VOD

## 模块划分
- common：公共工具、AOP鉴权、工具类、Redis/RabbitMQ封装
- model：实体、VO、查询对象
- server-gateway：网关，统一鉴权、跨域
- service-client：Feign接口 + 降级Fallback
- service：业务微服务
  - service-user：微信登录、VIP、收听历史
  - service-album：专辑、音频分类、上传、内容审核（本人负责模块）
  - service-search：ES搜索
  - service-account：账户充值扣减
  - service-order：订单管理
  - service-payment：微信支付V3
  - service-dispatch：XXL-JOB定时调度

## 核心功能
1. 专辑、音频三级分类管理，MinIO存储封面，腾讯VOD托管音频
2. AOP+注解+ThreadLocal实现跨服务统一鉴权，微信一键登录
3. ES+IK分词实现有声书全文检索
4. RabbitMQ异步解耦，Seata分布式事务保证订单和账户一致性
5. XXL-JOB定时任务、Canal数据同步、微信支付V3对接

## 启动说明
所有配置统一托管在 Nacos，仓库不存放 application.yml，仅 bootstrap.properties 指向Nacos地址。
