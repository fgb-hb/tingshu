package com.atguigu.tingshu.payment.service.impl;

import cn.hutool.core.util.IdUtil;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.model.payment.PaymentInfo;
import com.atguigu.tingshu.payment.config.WxPayV3Config;
import com.atguigu.tingshu.payment.service.PaymentInfoService;
import com.atguigu.tingshu.payment.service.WxPayService;
import com.atguigu.tingshu.payment.util.PayUtil;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.partnerpayments.app.model.Transaction;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.swing.plaf.ProgressBarUI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class WxPayServiceImpl implements WxPayService {

	@Autowired
	private PaymentInfoService paymentInfoService;

	@Autowired
	private WxPayV3Config wxPayV3Config;

	@Autowired
	private RSAAutoCertificateConfig rsaAutoCertificateConfig;

	/**
	 * 下单或充值选择微信支付，返回小程序拉起微信支付所需参数
	 *
	 * @param paymentType 支付类型：1301下单  1302充值
	 * @param orderNo     订单/充值订单编号
	 * @return {{"timeStamp":"","package":"","paySign":"","signType":"RSA","nonceStr":""}}}
	 */
	@Override
	public Map<String, String> createJsapi(String paymentType, String orderNo) {
		try {
			//1.获取或保存本地交易记录，验证状态
			PaymentInfo paymentInfo = paymentInfoService.savePaymentInfo(paymentType, orderNo);
			//支付状态：1401-未支付 1402-已支付
			String paymentStatus = paymentInfo.getPaymentStatus();
			if (!SystemConstant.PAYMENT_STATUS_UNPAID.equals(paymentStatus)) {
				throw new GuiguException(500, "本地交易记录状态有误" + paymentStatus);
			}
			//2.对接微信支付，获取小程序端拉起微信支付所需参数
			//2.1 创建调用微信支付JSAPI业务对象
			JsapiServiceExtension service = new JsapiServiceExtension.Builder().config(rsaAutoCertificateConfig).build();
			//2.2 创建预下单参数请求对象
			PrepayRequest request = new PrepayRequest();
			Amount amount = new Amount();
			//测试付款金额固定硬编码为1分
			amount.setTotal(1);
			request.setAmount(amount);
			request.setAppid(wxPayV3Config.getAppid());
			request.setMchid(wxPayV3Config.getMerchantId());
			request.setDescription(paymentInfo.getContent());
			request.setNotifyUrl(wxPayV3Config.getNotifyUrl());
			//商户订单编号
			request.setOutTradeNo(orderNo);
			//小程序目前还未上线，仅支持应用下的开发者用户进行付款 故这里需要设置开发者用户的openid
			Payer payer = new Payer();
			payer.setOpenid("odo3j4qp-wC3HVq9Z_D9C0cOr0Zs");
			request.setPayer(payer);
			//2.3 调用微信获取response包含了调起支付所需的所有参数，可直接用于前端调起支付
			PrepayWithRequestPaymentResponse response = service.prepayWithRequestPayment(request);
			if (response != null) {
				String timeStamp = response.getTimeStamp();
				String nonceStr = response.getNonceStr();
				String packageVal = response.getPackageVal();
				String signType = response.getSignType();
				String paySign = response.getPaySign();
				Map<String, String> map = new HashMap<>();
				map.put("timeStamp", timeStamp);
				map.put("nonceStr", nonceStr);
				map.put("package", packageVal);
				map.put("signType", signType);
				map.put("paySign", paySign);
				return map;
			}
			return null;
		} catch (Exception e) {
			log.error("微信下单失败");
			throw new GuiguException(500, e.getMessage());
		}
	}


	/**
	 * 查询订单支付状态
	 *
	 * @param orderNo
	 * @return
	 */
	@Override
	public boolean queryPayStatus(String orderNo) {
    /*//1.构建业务对象
    JsapiServiceExtension service = new JsapiServiceExtension.Builder().config(rsaAutoCertificateConfig).build();
    //2.构建根据商户订单号查询微信支付订单状态的请求参数
    QueryOrderByOutTradeNoRequest request = new QueryOrderByOutTradeNoRequest();
    request.setMchid(wxPayV3Config.getMerchantId());
    request.setOutTradeNo(orderNo)
    //3.调用微信支付查询订单状态接口
    Transaction transaction = service.queryOrderByOutTradeNo(request);
    if (transaction != null) {
        Transaction.TradeStateEnum tradeState = transaction.getTradeState();
        if (Transaction.TradeStateEnum.SUCCESS == tradeState) {
            //交易成功，验证订单金额跟用户实付金额是否一致
            Integer payerTotal = transaction.getAmount().getPayerTotal();
            if (payerTotal.intValue() == 1) {
                return true;
            }
        }
    }
    return false;*/
		//TODO 处理核心业务:更新本地交易记录状态

		//1.伪造微信交易对象 封装：订单编号+微信交易号
//		Transaction transaction = new Transaction();
//		transaction.setOutTradeNo(orderNo);
//		transaction.setTransactionId("WX"+ IdUtil.getSnowflakeNextId());
//		paymentInfoService.updatePaymentInfo(transaction);
		return true;
	}

	@Autowired
	private RedisTemplate redisTemplate;

	/**
	 * 提供给微信支付调用：用户微信付款成功后，微信通知商户支付结果
	 *
	 * @param request
	 * @return
	 */
	@Override
	public Map<String, String> notifyPayResult(HttpServletRequest request) {
//		//1.验签-避免出现虚假通知或支付结果在网络传输中被恶意篡改
//		//1.1 从请求头中获取封装请求参数对象数据
//		String signature = request.getHeader("Wechatpay-Signature");
//		String serial = request.getHeader("Wechatpay-Serial");
//		String nonce = request.getHeader("Wechatpay-Nonce");
//		String timestamp = request.getHeader("Wechatpay-Timestamp");
//		String signaureType = request.getHeader("Wechatpay-Signature-Type");
//		log.info("签名：{}，序列号：{}，随机数：{}，时间戳：{}，签名类型：{}", signature, serial, nonce, timestamp, signaureType);
//		//1.2 获取请求体中参数
//		String body = PayUtil.readData(request);
//		log.info("请求体：{}", body);
//
//		//2.参数解密-解析获取请求体中业务参数（支付结果业务数据）
//		//2.1 构造RequestParam
//		RequestParam requestParam = new RequestParam.Builder()
//				.serialNumber(serial)
//				.nonce(nonce)
//				.signature(signature)
//				.timestamp(timestamp)
//				.body(body)
//				.build();
//		//2.2 初始化 NotificationParser解析器对象
//		NotificationParser parser = new NotificationParser(rsaAutoCertificateConfig);
//		//2.3 解密并转换成 Transaction交易对象
//		Transaction transaction = parser.parse(requestParam, Transaction.class);
//
//		//3.验证微信支付状态：必须是已支付，应付金额跟实付金额一致
//		if (transaction != null) {
//			Transaction.TradeStateEnum tradeState = transaction.getTradeState();
//			if (Transaction.TradeStateEnum.SUCCESS == tradeState
//					&& transaction.getAmount().getPayerTotal().intValue() == 1) {
//
//				//4.幂等性处理 同一笔交易仅需处理一次
//				//4.1 获取交易对象中微信支付订单号或者商户订单作为唯一标识
//				String redisKey = RedisConstant.BUSINESS_PREFIX + transaction.getOutTradeNo();
//				//4.2 利用Redis提供set k v ex nx 命令实现
//				Boolean flag = redisTemplate.opsForValue().setIfAbsent(redisKey, transaction.getOutTradeNo(), 25, TimeUnit.HOURS);
//				if (flag) {
//					try {
//						//5.核心业务处理:更新本地交易记录
//						paymentInfoService.updatePaymentInfo(transaction);
//						//6.返回微信正确应答
//						Map<String, String> map = new HashMap<>();
//						map.put("code", "SUCCESS");
//						map.put("message", "成功");
//					} catch (Exception e) {
//						redisTemplate.delete(redisKey);
//						log.error("微信支付回调处理失败");
//						throw new RuntimeException(e);
//					}
//				}
//			}
//		}
		return null;
	}

}
