package com.atguigu.tingshu.album.service;

import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.vo.album.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface TrackInfoService extends IService<TrackInfo> {

    void saveTrackInfo(TrackInfoVo trackInfoVo, Long userId);

    Page<TrackListVo> getUserTrackPage(Page<TrackListVo> pageInfo, TrackInfoQuery trackInfoQuery);

    void updateTrackInfo(TrackInfo trackInfo);

    void removeTrackInfo(Long id);
    /**
     * 需求：用户未登录，可以给用户展示声音列表；用户已登录，可以给用户展示声音列表，并动态渲染付费标识
     * 分页查询专辑下声音列表（动态渲染付费标识）
     *
     * @param pageInfo MP分页对象
     * @param albumId 专辑ID
     * @param userId 用户ID
     * @return
     */
    IPage<AlbumTrackListVo> findAlbumTrackPage(IPage<AlbumTrackListVo> pageInfo, Long albumId, Long userId);

    /**
     * 更新DB中声音/专辑统计数值
     * @param mqVo
     */
    void updateTrackStat(TrackStatMqVo mqVo);

    /**
     * 根据声音ID查询声音统计信息
     * @param trackId
     * @return
     */
    TrackStatVo getTrackStatVo(Long trackId);

    /**
     *  查询用户未购买声音列表
     * @param trackId 选择购买声音ID，作为起始标准计算未购买声音数量
     * @param trackCount 限制查询未购买声音数量
     * @return
     */
    List<TrackInfo> findWaitBuyTrackInfoList(Long userId, Long trackId, Integer trackCount);

}
