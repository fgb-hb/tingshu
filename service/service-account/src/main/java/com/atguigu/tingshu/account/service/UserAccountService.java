package com.atguigu.tingshu.account.service;

import com.atguigu.tingshu.model.account.UserAccount;
import com.atguigu.tingshu.vo.account.AccountDeductVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.util.Map;

public interface UserAccountService extends IService<UserAccount> {


    void initUserAccount(Map<String, Object> map);

    /**
     * 查询用户账户可用余额
     * @param userId
     * @return
     */
    BigDecimal getAvailableAmount(Long userId);

    /**
     * 检查扣减账户余额
     * @param accountDeductVo
     */
    void deduct(AccountDeductVo accountDeductVo);


    void saveUserAccountDetail(Long userId, String content, String accountTradeTypeMinus, BigDecimal amount, String orderNo);
}
