package com.atguigu.tingshu.user.client.impl;


import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class UserDegradeFeignClient implements UserFeignClient {

    @Override
    public Result<UserInfoVo> getUserInfoVo(Long userId) {
        log.error("根据用户ID查询用户信息失败，用户ID：{}", userId);
        return null;
    }

    @Override
    public Result<Map<Long, Integer>> userIsPaidTrack(Long userId, Long albumId, List<Long> needCheckPayStatusTrackIdList) {
        log.error("【用户服务】提供userIsPaidTrack远程调用失败");
        return null;
    }

    @Override
    public Result<List<VipServiceConfig>> findAll() {
        log.error("【用户服务】提供findAll远程调用失败");
        return null;
    }

    @Override
    public Result<VipServiceConfig> getVipServiceConfig(Long id) {
        log.error("根据套餐ID查询套餐套餐详情失败，套餐ID：{}", id);
        return null;
    }

    @Override
    public Result<Boolean> isPaidAlbum(Long albumId) {
        log.error("判断当前用户是否购买过指定专辑失败，专辑ID：{}", albumId);
        return null;
    }

    @Override
    public Result<List<Long>> findUserPaidTrackList(Long albumId) {
        log.error("查询当前用户某个专辑下已购声音ID列表失败，专辑ID：{}", albumId);
        return null;
    }

    @Override
    public Result savePaidRecord(UserPaidRecordVo userPaidRecordVo) {
        log.error("【用户服务】提供savePaidRecord远程调用失败");
        return null;
    }




}
