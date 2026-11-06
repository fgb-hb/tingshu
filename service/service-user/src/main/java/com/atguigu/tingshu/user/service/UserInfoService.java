package com.atguigu.tingshu.user.service;

import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface UserInfoService extends IService<UserInfo> {

    Map<String, String> wxLogin(String code);

    UserInfoVo getUserInfo(Long userId);

    void updateUser(UserInfoVo userInfoVo);

    Map<Long, Integer> userIsPaidTrack(Long userId, Long albumId, List<Long> needCheckPayStatusTrackIdList);

    /**
     * 查询当前用户是否购买指定专辑
     * @param albumId
     * @return
     */
    Boolean isPaidAlbum(Long userId, Long albumId);

    /**
     * 查询当前用户某个专辑下已购声音ID列表
     * @param albumId
     * @return
     */
    List<Long> findUserPaidTrackList(Long userId, Long albumId);

    /**
     * 保存用户不同购买项目类型的购买记录
     * @param userPaidRecordVo
     */
    void savePaidRecord(UserPaidRecordVo userPaidRecordVo);

}
