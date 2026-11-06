package com.atguigu.tingshu.account.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.atguigu.tingshu.account.mapper.RechargeInfoMapper;
import com.atguigu.tingshu.account.mapper.UserAccountDetailMapper;
import com.atguigu.tingshu.account.mapper.UserAccountMapper;
import com.atguigu.tingshu.account.service.RechargeInfoService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.account.RechargeInfo;
import com.atguigu.tingshu.model.account.UserAccount;
import com.atguigu.tingshu.model.account.UserAccountDetail;
import com.atguigu.tingshu.vo.account.RechargeInfoVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rabbitmq.client.Return;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@SuppressWarnings({"all"})
public class RechargeInfoServiceImpl extends ServiceImpl<RechargeInfoMapper, RechargeInfo> implements RechargeInfoService {
	@Autowired
	private RechargeInfoMapper rechargeInfoMapper;
@Autowired
private UserAccountMapper userAccountMapper;
@Autowired
private UserAccountDetailMapper userAccountDetailMapper;
	@Override
	public RechargeInfo getRechargeInfo(String orderNo) {
		QueryWrapper<RechargeInfo> queryWrapper = new QueryWrapper<>();
		queryWrapper.eq("order_no", orderNo);
		return this.getOne(queryWrapper);
	}
	/**
	 * 保存充值
	 *
	 * @param rechargeInfoVo
	 * @return {orderNo:"充值订单编号"}
	 */
	@Override
	public Map<String, String> submitRecharge(RechargeInfoVo rechargeInfoVo) {
		RechargeInfo rechargeInfo = new RechargeInfo();
		rechargeInfo.setUserId(AuthContextHolder.getUserId());
		//生成充值订单编号
		String orderNo = "CZ" + DateUtil.today().replace("-", "") + IdUtil.getSnowflakeNextId();
		rechargeInfo.setOrderNo(orderNo);
		rechargeInfo.setRechargeStatus(SystemConstant.ORDER_STATUS_UNPAID);
		rechargeInfo.setRechargeAmount(rechargeInfoVo.getAmount());
		rechargeInfo.setPayWay(rechargeInfoVo.getPayWay());
		rechargeInfoMapper.insert(rechargeInfo);

		//TODO 超时未支付充值订单关闭，RabbitMQ延迟消息

		Map<String, String> map = new HashMap<>();
		map.put("orderNo", orderNo);
		return map;
	}
	/**
	 * 微信支付成功后，处理充值业务
	 *
	 * @param orderNo
	 * @return
	 */
	@Override
	public void rechargePaySuccess(String orderNo) {
		//1.根据充值订单编号查询充值记录
		RechargeInfo rechargeInfo = rechargeInfoMapper.selectOne(new LambdaQueryWrapper<RechargeInfo>().eq(RechargeInfo::getOrderNo, orderNo));
		if (rechargeInfo == null) {
			throw new GuiguException(500,"充值订单不存在");
		}
		//2.修改记录
		rechargeInfo.setRechargeStatus(SystemConstant.ORDER_STATUS_PAID);
		rechargeInfo.setUpdateTime(new Date());
		rechargeInfoMapper.updateById(rechargeInfo);
		//3.更新用户余额
		BigDecimal amount = rechargeInfo.getRechargeAmount();
		int rows = userAccountMapper.update(
				null,
				new LambdaUpdateWrapper<UserAccount>()
						.setSql("total_amount = total_amount +" + amount)
						.setSql("available_amount = available_amount +" + amount)
						.setSql("total_income_amount = total_income_amount +" + amount)
						.eq(UserAccount::getUserId, rechargeInfo.getUserId())
		);
		if (rows < 0) {
			throw new GuiguException(500,"充值失败");
		}
		//4.新增用户充值记录明细
		UserAccountDetail userAccountDetail = new UserAccountDetail();
		userAccountDetail.setUserId(rechargeInfo.getUserId());
		userAccountDetail.setTitle("充值");
		userAccountDetail.setTradeType("1201");
		userAccountDetail.setAmount(amount);
		userAccountDetail.setOrderNo(orderNo);
		userAccountDetailMapper.insert(userAccountDetail);
	}

	/**
	 * 根据交易类型分页查询账户变动日志
	 *
	 * @param pageInfo
	 * @param tradeType
	 * @param userId
	 * @return
	 */
	@Override
	public Result<Page<UserAccountDetail>> getUserAccountDetailPage(Page<UserAccountDetail> pageInfo, String tradeType, Long userId) {
		Page<UserAccountDetail> userAccountDetailPage = userAccountDetailMapper.selectPage
				(pageInfo, new LambdaQueryWrapper<UserAccountDetail>().eq(UserAccountDetail::getUserId, userId).eq(UserAccountDetail::getTradeType, tradeType)
						.select(UserAccountDetail::getOrderNo, UserAccountDetail::getTradeType, UserAccountDetail::getTitle, UserAccountDetail::getAmount));
		return Result.ok(userAccountDetailPage);
	}

}