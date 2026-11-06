package com.atguigu.tingshu.search.service;

import com.atguigu.tingshu.vo.album.TrackStatMqVo;

import java.util.Map;

public interface ItemService {


    Map<String, Object> getItem(Long albumId);

    void updateTrackStat(TrackStatMqVo mqVo);
}
