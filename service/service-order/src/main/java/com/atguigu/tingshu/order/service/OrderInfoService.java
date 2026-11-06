package com.atguigu.tingshu.order.service;

import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.vo.order.OrderInfoVo;
import com.atguigu.tingshu.vo.order.TradeVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

public interface OrderInfoService extends IService<OrderInfo> {


    /**
     * 三种商品（VIP会员、专辑、声音）订单结算,渲染订单结算页面
     *
     * @param tradeVo (购买项目类型、购买项目ID、声音数量)
     * @return 订单VO信息
     */
    OrderInfoVo trade(TradeVo tradeVo);

    /**
     * 提交订单
     * @param userId
     * @param orderInfoVo
     * @return
     */
    Map<String, String> submitOrder(Long userId, OrderInfoVo orderInfoVo);

    /**
     * 根据订单号查询订单详情
     * @param orderNo 订单号
     * @return 订单信息
     */
    OrderInfo getOrderInfo(String orderNo);

    /**
     * 分页查询当前登录用户订单列表（包含订单商品明细）
     * @param pageParam 分页参数
     * @param userId 用户ID
     * @return 分页订单信息
     */
    Page<OrderInfo> findUserPage(Page<OrderInfo> pageParam, Long userId);

    /**
     * 保存订单相关信息
     * @param userId
     * @param orderInfoVo
     * @return
     */
    OrderInfo saveOrderInfo(Long userId, OrderInfoVo orderInfoVo);
    /**
     * 查询并且根据状态取消订单
     * @param orderId
     */
    void cancelOrder(Long orderId);

    /**
     * 用户付款成功后，处理订单相关业务
     * @param orderNo
     * @return
     */
    void orderPaySuccess(String orderNo);

}
