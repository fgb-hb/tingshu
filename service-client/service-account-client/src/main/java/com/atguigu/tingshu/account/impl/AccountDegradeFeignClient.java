package com.atguigu.tingshu.account.impl;


import com.atguigu.tingshu.account.AccountFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.account.RechargeInfo;
import com.atguigu.tingshu.vo.account.AccountDeductVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
@Slf4j
@Component
public class AccountDegradeFeignClient implements AccountFeignClient {
    @Override
    public Result checkAndDeduct(AccountDeductVo accountDeductVo) {
        log.error("【用户服务】提供checkAndDeduct远程调用失败");
        return null;
    }

    @Override
    public Result<RechargeInfo> getRechargeInfo(String orderNo) {
        log.error("【用户服务】提供getRechargeInfo远程调用失败");
        return null;
    }

    @Override
    public Result rechargePaySuccess(String orderNo) {
        log.error("[账户服务]提供远程调用rechargePaySuccess方法执行服务降级");
        return null;
    }

}