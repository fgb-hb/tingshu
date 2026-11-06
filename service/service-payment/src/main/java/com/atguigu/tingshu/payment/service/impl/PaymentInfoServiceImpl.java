package com.atguigu.tingshu.payment.service.impl;

import com.atguigu.tingshu.account.AccountFeignClient;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.account.RechargeInfo;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.model.payment.PaymentInfo;
import com.atguigu.tingshu.order.client.OrderFeignClient;
import com.atguigu.tingshu.payment.mapper.PaymentInfoMapper;
import com.atguigu.tingshu.payment.service.PaymentInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wechat.pay.java.service.partnerpayments.app.model.Transaction;
import io.seata.spring.annotation.GlobalTransactional;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@SuppressWarnings({"all"})
public class PaymentInfoServiceImpl extends ServiceImpl<PaymentInfoMapper, PaymentInfo> implements PaymentInfoService {

	@Autowired
	private OrderFeignClient orderFeignClient;

	@Autowired
	private AccountFeignClient accountFeignClient;
	@Autowired
	private PaymentInfoMapper paymentInfoMapper;

	@Override
	public PaymentInfo savePaymentInfo(String paymentType, String orderNo) {
		//1.根据支付类型和订单号，查询对应的业务数据，并封装交易记录
		if (SystemConstant.PAYMENT_TYPE_ORDER.equals(paymentType)) {
			//2.1 查询订单信息
			Result<OrderInfo> orderInfoResult = orderFeignClient.getOrderInfo(orderNo);
			OrderInfo orderInfo = orderInfoResult.getData();
			if (null != orderInfo) {
				PaymentInfo paymentInfo = new PaymentInfo();
				paymentInfo.setUserId(orderInfo.getUserId());
				paymentInfo.setPayWay(orderInfo.getPayWay());
				paymentInfo.setAmount(orderInfo.getOrderAmount());
				paymentInfo.setContent(orderInfo.getOrderTitle());
				return this.saveLocalPayment(paymentType, orderNo, paymentInfo);
			}
		} else if (SystemConstant.PAYMENT_TYPE_RECHARGE.equals(paymentType)) {
			//2.2 查询充值记录
			Result<RechargeInfo> rechargeInfoResult = accountFeignClient.getRechargeInfo(orderNo);
			RechargeInfo rechargeInfo = rechargeInfoResult.getData();
			if (null != rechargeInfo) {
				PaymentInfo paymentInfo = new PaymentInfo();
				paymentInfo.setUserId(rechargeInfo.getUserId());
				paymentInfo.setPayWay(rechargeInfo.getPayWay());
				paymentInfo.setAmount(rechargeInfo.getRechargeAmount());
				paymentInfo.setContent("充值");
				return this.saveLocalPayment(paymentType, orderNo, paymentInfo);
			}
		}
		return null;
	}

	/**
	 * 更新交易记录
	 * @param transaction
	 */
	@Override
	@GlobalTransactional(rollbackFor = Exception.class)
	public void updatePaymentInfo(String outTradeNo) {
		//1.根据订单号查询交易记录
		PaymentInfo paymentInfo = paymentInfoMapper.selectOne(new LambdaQueryWrapper<PaymentInfo>().eq(PaymentInfo::getOrderNo, outTradeNo));
		if (null == paymentInfo) {
			throw new IllegalArgumentException("交易记录不存在");
		}
		//2.交易存在，更新支付状态为已支付
       paymentInfoMapper.update(null,new LambdaUpdateWrapper<PaymentInfo>()
			   .eq(PaymentInfo::getOrderNo, outTradeNo)
			   .eq(PaymentInfo::getPaymentStatus, SystemConstant.PAYMENT_STATUS_UNPAID)
			   .set(PaymentInfo::getPaymentStatus, SystemConstant.PAYMENT_STATUS_PAID)
			   .set(PaymentInfo::getCallbackTime, new Date())
			   .set(PaymentInfo::getOutTradeNo, outTradeNo)
			   .set(PaymentInfo::getCallbackContent, "支付成功"));
		//3.判断交易类型

		String paymentType = paymentInfo.getPaymentType();
		if (SystemConstant.PAYMENT_TYPE_ORDER.equals(paymentType)) {
			Result result = orderFeignClient.orderPaySuccess(paymentInfo.getOrderNo());
			if (result.getCode().intValue() != 200) {
				throw new GuiguException(result.getCode(), result.getMessage());
			}
		}
		if (SystemConstant.PAYMENT_TYPE_RECHARGE.equals(paymentType)) {
			//4.1 远程调用账户服务，修改充值状态，完车充值业务
			Result result = accountFeignClient.rechargePaySuccess(paymentInfo.getOrderNo());
			if (result.getCode().intValue() != 200) {
				throw new GuiguException(result.getCode(), result.getMessage());
			}
		}
	}

	/**
	 * 设置交易记录公共字段并保存到本地
	 */
	private PaymentInfo saveLocalPayment(String paymentType, String orderNo, PaymentInfo paymentInfo) {
		//1.设置支付类型、订单号
		paymentInfo.setPaymentType(paymentType);
		paymentInfo.setOrderNo(orderNo);
		//2.设置默认支付状态为未支付
		paymentInfo.setPaymentStatus(SystemConstant.PAYMENT_STATUS_UNPAID);
		//3.保存
		this.save(paymentInfo);
		return paymentInfo;
	}

}