package com.atguigu.tingshu.payment.service;

import com.atguigu.tingshu.model.payment.PaymentInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wechat.pay.java.service.partnerpayments.app.model.Transaction;

public interface PaymentInfoService extends IService<PaymentInfo> {

	/**
	 * 封装本地交易记录
	 *
	 * @param paymentType 支付类型：1301-订单 1302-充值
	 * @param orderNo     订单编号
	 * @return 本地交易记录
	 */
	PaymentInfo savePaymentInfo(String paymentType, String orderNo);

	void updatePaymentInfo(String outTradeNo);

}