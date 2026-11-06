package com.atguigu.tingshu.account.service;

import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.account.RechargeInfo;
import com.atguigu.tingshu.model.account.UserAccountDetail;
import com.atguigu.tingshu.vo.account.RechargeInfoVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

public interface RechargeInfoService extends IService<RechargeInfo> {

	/**
	 * 根据订单编号查询充值记录
	 *
	 * @param orderNo 充值订单编号
	 * @return 充值记录
	 */
	RechargeInfo getRechargeInfo(String orderNo);

	/**
	 * 保存充值记录
	 * @param rechargeInfoVo
	 * @return {orderNo:"充值订单编号"}
	 */
	Map<String, String> submitRecharge(RechargeInfoVo rechargeInfoVo);

	/**
	 * 	微信支付成功后，处理充值业务
	 * @param orderNo
	 * @return
	 */
	void rechargePaySuccess(String orderNo);

	/**
	 * 获取用户充值记录分页列表
	 * @param pageInfo
	 * @param accountTradeTypeDeposit
	 * @param userId
	 */
	Result<Page<UserAccountDetail>> getUserAccountDetailPage(Page<UserAccountDetail> pageInfo, String accountTradeTypeDeposit, Long userId);
}