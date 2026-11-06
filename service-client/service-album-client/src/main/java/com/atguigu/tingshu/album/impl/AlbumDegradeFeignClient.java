package com.atguigu.tingshu.album.impl;


import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.*;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AlbumDegradeFeignClient implements AlbumFeignClient {

    @Override
    public Result<AlbumInfo> getAlbumInfo(Long id) {
        log.info("根据专辑ID查询专辑信息（包括专辑标签列表）失败，专辑ID：{}", id);
        return null;
    }

    @Override
    public Result<BaseCategoryView> getCategoryView(Long category3Id) {
        log.info("根据三级分类编号查询分类视图失败，三级分类编号：{}", category3Id);
        return null;
    }
    @Override
    public Result<List<BaseCategory3>> findTopBaseCategory3(Long category1Id) {
        log.error("[专辑服务]远程调用findTopBaseCategory3执行服务降级");
        return null;
    }

    @Override
    public Result<AlbumStatVo> getAlbumStatVo(Long albumId) {
        log.info("根据专辑ID查询专辑统计信息失败，专辑ID：{}", albumId);
        return null;
    }
    @Override
    public Result<List<BaseCategory1>> getAllCategory1() {
        log.error("远程调用专辑服务getAllCategory1服务降级");
        return null;
    }

    @Override
    public Result<List<TrackInfo>> findWaitBuyTrackInfoList(Long trackId, Integer trackCount) {
        log.info("根据用户ID查询用户待购买的专辑列表失败，用户ID：{}", trackId);
        return null;
    }

    @Override
    public Result<List<Map<String, Object>>> getUserTrackPaidList(Long trackId) {
        log.info("根据用户ID查询用户已购买的专辑列表失败，用户ID：{}", trackId);
        return null;
    }

    @Override
    public Result<TrackInfo> getTrackInfo(Long id) {
        log.info("根据声音ID查询声音信息失败，声音ID：{}", id);
        return null;
    }

}
