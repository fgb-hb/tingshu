package com.atguigu.tingshu.album.service;

import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumCategoryVo;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface AlbumInfoService extends IService<AlbumInfo> {


    void saveAlbumInfo(AlbumInfoVo albumInfoVo, Long userId);

    Page<AlbumListVo> findUserAlbumPage(Page<AlbumListVo> pageParam, AlbumInfoQuery albumInfoQuery);

    void removeAlbumInfo(Long id);

    AlbumInfo getAlbumInfo(Long id);

    void updateAlbumInfo(Long id, AlbumInfoVo albumInfoVo);

    List<AlbumInfo> getUserAllAlbumList(Long userId);
    /**
     * 根据专辑ID查询专辑统计信息
     * @param albumId
     * @return
     */
    AlbumStatVo getAlbumStatVo(Long albumId);

    /**
     * 基于当前用户未购买声音数量动态渲染分集购买列表
     * @param userId 用户ID
     * @param trackId 选择购买声音ID，作为起始标准计算未购买声音数量
     * @return
     */
    List<Map<String, Object>> findUserTrackPaidList(Long userId, Long trackId);

}
