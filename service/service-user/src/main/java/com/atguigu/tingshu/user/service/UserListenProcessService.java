package com.atguigu.tingshu.user.service;

import com.atguigu.tingshu.vo.user.UserListenProcessVo;

import java.math.BigDecimal;
import java.util.Map;

public interface UserListenProcessService {

    BigDecimal getTrackBreakSecond(Long userId, Long trackId);

    String getCollectionName(Long userId);
    /**
     * 更新声音播放进度
     * @param userListenProcessVo
     * @return
     */
    void updateUserListenProcess(Long userId, UserListenProcessVo userListenProcessVo);

    /**
     * 获取当前用户最近播放声音
     * @param userId
     * @return
     */
    Map<String, Long> getLatelyTrack(Long userId);
}
