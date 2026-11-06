package com.atguigu.tingshu.account.api;

import com.atguigu.tingshu.account.service.RechargeInfoService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.login.Fgb;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.account.RechargeInfo;
import com.atguigu.tingshu.model.account.UserAccountDetail;
import com.atguigu.tingshu.vo.account.RechargeInfoVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "充值管理")
@RestController
@RequestMapping("api/account")
@SuppressWarnings({"all"})
public class RechargeInfoApiController {

	@Autowired
	private RechargeInfoService rechargeInfoService;

	/**
	 * 根据订单编号查询充值记录
	 *
	 * @param orderNo 充值订单编号
	 * @return 充值记录
	 */
	@Operation(summary = "根据订单编号查询充值记录")
	@GetMapping("/rechargeInfo/getRechargeInfo/{orderNo}")
	public Result<RechargeInfo> getRechargeInfo(@PathVariable String orderNo) {
		RechargeInfo rechargeInfo = rechargeInfoService.getRechargeInfo(orderNo);
		return Result.ok(rechargeInfo);
	}

	/**
	 * 保存充值
	 * @param rechargeInfoVo
	 * @return {orderNo:"充值订单编号"}
	 */
	@Fgb
	@Operation(summary = "保存充值")
	@PostMapping("/rechargeInfo/submitRecharge")
	public Result<Map<String, String>> submitRecharge(@RequestBody RechargeInfoVo rechargeInfoVo){
		Map<String, String> map = rechargeInfoService.submitRecharge(rechargeInfoVo);
		return Result.ok(map);
	}

	/**
	 * 	微信支付成功后，处理充值业务
	 * @param orderNo
	 * @return
	 */
	@Operation(summary = "微信支付成功后，处理充值业务")
	@GetMapping("/rechargeInfo/rechargePaySuccess/{orderNo}")
	public Result rechargePaySuccess(@PathVariable String orderNo){
		rechargeInfoService.rechargePaySuccess(orderNo);
		return Result.ok();
	}

	/**
	 * 查询用户充值记录
	 *
	 * @param page
	 * @param limit
	 * @return
	 */
	@Fgb
	@Operation(summary = "查询用户充值记录")
	@GetMapping("/userAccount/findUserRechargePage/{page}/{limit}")
	public Result<Page<UserAccountDetail>> getUserRechargePage(@PathVariable int page, @PathVariable int limit) {
		Long userId = AuthContextHolder.getUserId();
		Page<UserAccountDetail> pageInfo = new Page<>(page, limit);
		rechargeInfoService.getUserAccountDetailPage(pageInfo, SystemConstant.ACCOUNT_TRADE_TYPE_DEPOSIT, userId);
		return Result.ok(pageInfo);
	}


	/**
	 * 查询用户消费记录
	 *
	 * @param page
	 * @param limit
	 * @return
	 */
	@Fgb
	@Operation(summary = "查询用户消费记录")
	@GetMapping("/userAccount/findUserConsumePage/{page}/{limit}")
	public Result<Page<UserAccountDetail>> getUserConsumePage(@PathVariable int page, @PathVariable int limit) {
		Long userId = AuthContextHolder.getUserId();
		Page<UserAccountDetail> pageInfo = new Page<>(page, limit);
		rechargeInfoService.getUserAccountDetailPage(pageInfo, SystemConstant.ACCOUNT_TRADE_TYPE_MINUS, userId);
		return Result.ok(pageInfo);
	}


}