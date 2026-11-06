package com.atguigu.tingshu.account.service.impl;

import com.atguigu.tingshu.account.mapper.UserAccountDetailMapper;
import com.atguigu.tingshu.account.mapper.UserAccountMapper;
import com.atguigu.tingshu.account.service.UserAccountService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.model.account.UserAccount;
import com.atguigu.tingshu.model.account.UserAccountDetail;
import com.atguigu.tingshu.vo.account.AccountDeductVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class UserAccountServiceImpl extends ServiceImpl<UserAccountMapper, UserAccount> implements UserAccountService {

	@Autowired
	private UserAccountMapper userAccountMapper;
	@Autowired
	private UserAccountDetailMapper userAccountDetailMapper;
@Transactional(rollbackFor = Exception.class)
	@Override
	public void initUserAccount(Map<String, Object> map) {
		// 初始化用户账号
		UserAccount userAccount = new UserAccount();
		userAccount.setTotalAmount((BigDecimal) map.get("amount"));
		userAccount.setAvailableAmount((BigDecimal) map.get("amount"));
		userAccount.setTotalIncomeAmount((BigDecimal) map.get("amount"));
		userAccount.setUserId((Long) map.get("user_id"));
	   // 插入用户账号
		userAccountMapper.insert(userAccount);
		// 创建账户明细
		UserAccountDetail userAccountDetail = new UserAccountDetail();
		userAccountDetail.setUserId((Long) map.get("user_id"));
		userAccountDetail.setTitle((String) map.get("title"));
		userAccountDetail.setAmount((BigDecimal) map.get("amount"));
		userAccountDetail.setOrderNo((String) map.get("order_no"));
		userAccountDetail.setTradeType( SystemConstant.ACCOUNT_TRADE_TYPE_DEPOSIT);
    // 插入账户明细
   userAccountDetailMapper.insert(userAccountDetail);
	}
	/**
	 * 查询用户账户可用余额
	 * @param userId
	 * @return
	 */
	@Override
	public BigDecimal getAvailableAmount(Long userId) {
		UserAccount userAccount = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
				.eq(UserAccount::getUserId, userId)
				.select(UserAccount::getAvailableAmount));
		if (userAccount == null) {
			throw new IllegalArgumentException("用户账号不存在");
		}
		return userAccount.getAvailableAmount();
	}
	/**
	 * 检查扣减账户余额
	 * 避免账户余额超扣，采用分布式锁；数据库锁（悲观锁或乐观锁）
	 *
	 * @param accountDeductVo
	 */
	@Override
//@Transactional(rollbackFor = Exception.class) //TODO 引入Seata一定要去掉,否则会导致分布式事务失效
	public void deduct(AccountDeductVo accountDeductVo) {
		//1.采用数据库悲观锁，检查账户余额是否满足扣款需求
		BigDecimal amount = accountDeductVo.getAmount();
		Long userId = accountDeductVo.getUserId();
		UserAccount userAccount = userAccountMapper.checkDeduction(userId, amount);
		if (userAccount == null) {
			throw new GuiguException(500, "账户余额不足！");
		}
		//2.扣减账户余额
		userAccountMapper.update(
				null,
				new LambdaUpdateWrapper<UserAccount>()
						.eq(UserAccount::getUserId, userId)
						.setSql("total_amount = total_amount -" + amount)
						.setSql("available_amount = available_amount -" + amount)
						.setSql("total_pay_amount = total_pay_amount +" + amount)
		);
		//3.新增账户变动日志
		this.saveUserAccountDetail(accountDeductVo.getUserId(), accountDeductVo.getContent(), SystemConstant.ACCOUNT_TRADE_TYPE_MINUS, accountDeductVo.getAmount(), accountDeductVo.getOrderNo());
	}

	@Override
	public void saveUserAccountDetail(Long userId, String content, String accountTradeTypeMinus, BigDecimal amount, String orderNo) {
		// 新增账户变动日志
    UserAccountDetail userAccountDetail = new UserAccountDetail();
    userAccountDetail.setUserId(userId);
    userAccountDetail.setTitle(content);
    userAccountDetail.setTradeType(accountTradeTypeMinus);
    userAccountDetail.setAmount(amount);
    userAccountDetail.setOrderNo(orderNo);
    userAccountDetailMapper.insert(userAccountDetail);

	}

}
