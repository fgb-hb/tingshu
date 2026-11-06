package com.atguigu.tingshu.payment.api;

import com.atguigu.tingshu.common.login.Fgb;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.payment.service.WxPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "微信支付接口")
@RestController
@RequestMapping("api/payment")
@Slf4j
public class WxPayApiController {

    @Autowired
    private WxPayService wxPayService;

    /**
     * 下单或充值选择微信支付，返回小程序拉起微信支付所需参数
     * @param paymentType 支付类型：1301下单  1302充值
     * @param orderNo 订单/充值订单编号
     * @return {"timeStamp":"","package":"","paySign":"","signType":"RSA","nonceStr":""}
     */
    @Fgb
    @Operation(summary = "下单或充值选择微信支付，返回小程序拉起微信支付所需参数")
    @PostMapping("/wxPay/createJsapi/{paymentType}/{orderNo}")
    public Result<Map<String, String>> createJsapi(@PathVariable String paymentType,
                                                   @PathVariable String orderNo){
        Map<String, String> map = wxPayService.createJsapi(paymentType, orderNo);
        return Result.ok(map);
    }

    /**
     * 查询订单支付状态
     * @param orderNo
     * @return
     */
    @Fgb
    @Operation(summary = "商户端主动查询微信支付结果")
    @GetMapping("/wxPay/queryPayStatus/{orderNo}")
    public Result<Boolean> queryPayStatus(@PathVariable String orderNo){
        boolean result = wxPayService.queryPayStatus(orderNo);
        return Result.ok(result);
    }

    /**
     * 微信支付成功后异步回调
     * @param request
     * @return
     * @throws Exception
     */
    @Operation(summary = "微信支付成功后异步回调")
    @PostMapping("/wxPay/notify")
    public Map<String, String> wxPayNotify(HttpServletRequest request) throws Exception {
        return wxPayService.notifyPayResult(request);
    }
}
