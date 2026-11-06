package com.atguigu.tingshu.user.client;

import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.user.client.impl.UserDegradeFeignClient;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户模块远程调用API接口
 * </p>
 *
 * @author atguigu
 */
@FeignClient(value = "service-user", fallback = UserDegradeFeignClient.class,path = "api/user")
public interface UserFeignClient {
    /**
     * 根据用户ID查询用户/主播基本信息
     * @param userId
     * @return
     */

    @GetMapping("/userInfo/getUserInfoVo/{userId}")
    public Result<UserInfoVo> getUserInfoVo(@PathVariable Long userId);

    /**
     * 提交需要检查购买状态声音ID列表，响应每个声音购买状态
     * @param userId
     * @param albumId
     * @param needCheckPayStatusTrackIdList 待检查购买状态声音ID列表
     * @return
     */
    @PostMapping("/userInfo/userIsPaidTrack/{userId}/{albumId}")
    public Result<Map<Long, Integer>> userIsPaidTrack(
            @PathVariable Long userId,
            @PathVariable Long albumId,
            @RequestBody List<Long> needCheckPayStatusTrackIdList
    );
    /**
     * 获取vip套餐列表
     *
     * @return
     */
    @GetMapping("/vipServiceConfig/findAll")
    public Result<List<VipServiceConfig>> findAll();
    /**
     * 根据套餐ID查询vip套餐详情
     *
     * @param id
     * @return
     */
    @GetMapping("/vipServiceConfig/getVipServiceConfig/{id}")
    public Result<VipServiceConfig> getVipServiceConfig(@PathVariable Long id);

    /**
     * 判断当前用户是否购买过指定专辑
     * @param albumId
     * @return
     */
    @GetMapping("/userInfo/isPaidAlbum/{albumId}")
    public Result<Boolean> isPaidAlbum(@PathVariable Long albumId);

    /**
     * 查询当前用户某个专辑下已购声音ID列表
     * @param albumId
     * @return
     */
    @GetMapping("/userInfo/findUserPaidTrackList/{albumId}")
    public Result<List<Long>> findUserPaidTrackList(@PathVariable Long albumId);

    /**
     * 用户支付成功后，虚拟物品发货
     * @param userPaidRecordVo
     * @return
     */
    @PostMapping("/userInfo/savePaidRecord")
    public Result savePaidRecord(@RequestBody UserPaidRecordVo userPaidRecordVo);



}
