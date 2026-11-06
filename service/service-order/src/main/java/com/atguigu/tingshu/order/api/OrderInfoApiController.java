package com.atguigu.tingshu.order.api;

import com.atguigu.tingshu.common.login.Fgb;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.order.service.OrderInfoService;
import com.atguigu.tingshu.vo.order.OrderInfoVo;
import com.atguigu.tingshu.vo.order.TradeVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "订单管理")
@RestController
@RequestMapping("api/order")
@SuppressWarnings({"all"})
public class OrderInfoApiController {

	@Autowired
	private OrderInfoService orderInfoService;

	/**
	 * 三种商品（VIP会员、专辑、声音）订单结算,渲染订单结算页面
	 *
	 * @param tradeVo (购买项目类型、购买项目ID、声音数量)
	 * @return 订单VO信息
	 */
	@Fgb
	@Operation(summary = "三种商品（VIP会员、专辑、声音）订单结算")
	@PostMapping("/orderInfo/trade")
	public Result<OrderInfoVo> trade(@RequestBody TradeVo tradeVo) {
		OrderInfoVo orderInfoVo = orderInfoService.trade(tradeVo);
		return Result.ok(orderInfoVo);
	}

	/**
	 * 提交订单
	 *
	 * @param orderInfoVo
	 * @return
	 */
	@Fgb
	@Operation(summary = "提交订单")
	@PostMapping("/orderInfo/submitOrder")
	public Result<Map<String, String>> submitOrder(@RequestBody @Validated OrderInfoVo orderInfoVo) {
		//1.获取当前登录用户ID
		Long userId = AuthContextHolder.getUserId();
		//2.调用业务逻辑提交订单
		Map<String, String> map = orderInfoService.submitOrder(userId, orderInfoVo);
		return Result.ok(map);
	}

	/**
	 * 根据订单号查询订单详情
	 *
	 * @param orderNo 订单号
	 * @return 订单信息
	 */
	@Fgb
	@Operation(summary = "根据订单号查询订单详情")
	@GetMapping("/orderInfo/getOrderInfo/{orderNo}")
	public Result<OrderInfo> getOrderInfo(@PathVariable String orderNo) {
		OrderInfo orderInfo = orderInfoService.getOrderInfo(orderNo);
		return Result.ok(orderInfo);
	}

	/**
	 * 分页查询当前登录用户订单列表（包含订单商品明细）
	 *
	 * @param page  页码
	 * @param limit 页大小
	 * @return 分页订单信息
	 */
	@Fgb
	@Operation(summary = "分页查询当前登录用户订单列表（包含订单商品明细）")
	@GetMapping("/orderInfo/findUserPage/{page}/{limit}")
	public Result<Page<OrderInfo>> findUserPage(@PathVariable Long page, @PathVariable Long limit) {
		//1.获取当前登录用户ID
		Long userId = AuthContextHolder.getUserId();
		//2.封装分页参数：页码、页大小
		Page<OrderInfo> pageParam = new Page<>(page, limit);
		//3.调用业务逻辑层完成分页查询
		pageParam = orderInfoService.findUserPage(pageParam, userId);
		return Result.ok(pageParam);
	}
	/**
	 * 用户付款成功后，处理订单相关业务
	 * @param orderNo
	 * @return
	 */
	@Operation(summary = "用户付款成功后，处理订单相关业务")
	@GetMapping("/orderInfo/orderPaySuccess/{orderNo}")
	public Result orderPaySuccess(@PathVariable String orderNo){
		orderInfoService.orderPaySuccess(orderNo);
		return Result.ok();
	}

}