package com.atguigu.tingshu.payment.service;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

public interface WxPayService {

    /**
     * 下单或充值选择微信支付，返回小程序拉起微信支付所需参数
     * @param paymentType 支付类型：1301下单  1302充值
     * @param orderNo 订单/充值订单编号
     * @return {"timeStamp":"","package":"","paySign":"","signType":"RSA","nonceStr":""}
     */

    Map<String, String> createJsapi(String paymentType, String orderNo);

    /**
     * 查询订单支付状态
     * @param orderNo
     * @return
     */

    boolean queryPayStatus(String orderNo);
    /**
     * 提供给微信支付调用：用户微信付款成功后，微信通知商户支付结果
     * @param request
     * @return {code:"SUCCESS",message:"成功"}
     */
    Map<String, String> notifyPayResult(HttpServletRequest request);

}
