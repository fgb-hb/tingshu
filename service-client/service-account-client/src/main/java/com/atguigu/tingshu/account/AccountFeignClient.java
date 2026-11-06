package com.atguigu.tingshu.account;

import com.atguigu.tingshu.account.impl.AccountDegradeFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.account.RechargeInfo;
import com.atguigu.tingshu.vo.account.AccountDeductVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * <p>
 * 账号模块远程调用API接口
 * </p>
 *
 * @author atguigu
 */
@FeignClient(value = "service-account", path = "api/account", fallback = AccountDegradeFeignClient.class)
public interface AccountFeignClient {
    /**
     * 扣减账户余额
     *
     * @param accountDeductVo
     * @return
     */
    @PostMapping("/userAccount/checkAndDeduct")
    public Result checkAndDeduct(@RequestBody AccountDeductVo accountDeductVo);

    /**
     * 根据订单编号查询充值记录
     *
     * @param orderNo 充值订单编号
     * @return 充值记录
     */
    @GetMapping("/rechargeInfo/getRechargeInfo/{orderNo}")
    public Result<RechargeInfo> getRechargeInfo(@PathVariable("orderNo") String orderNo);

    /**
     * 	微信支付成功后，处理充值业务
     * @param orderNo
     * @return
     */
    @GetMapping("/rechargeInfo/rechargePaySuccess/{orderNo}")
    public Result rechargePaySuccess(@PathVariable String orderNo);

}