package com.atguigu.tingshu.album;

import com.atguigu.tingshu.album.impl.AlbumDegradeFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.*;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 专辑模块远程调用Feign接口
 * </p>
 *
 * @author atguigu
 */
@FeignClient(value = "service-album", fallback = AlbumDegradeFeignClient.class,path = "api/album")
public interface AlbumFeignClient {

    @GetMapping("/albumInfo/getAlbumInfo/{id}")
    public Result<AlbumInfo> getAlbumInfo(@PathVariable Long id);

    /**
     * 根据三级分类ID查询分类视图
     * @param category3Id
     * @return
     */
    @GetMapping("/category/getCategoryView/{category3Id}")
    public Result<BaseCategoryView> getCategoryView(@PathVariable Long category3Id);

    /**
     * 根据一级分类Id查询置顶7个三级分类列表
     * @param category1Id
     * @return
     */
    @GetMapping("/category/findTopBaseCategory3/{category1Id}")
    public Result<List<BaseCategory3>> findTopBaseCategory3(@PathVariable Long category1Id);
    /**
     * 根据专辑ID查询专辑统计信息
     * @param albumId
     * @return
     */
    @GetMapping("/albumInfo/getAlbumStatVo/{albumId}")
    public Result<AlbumStatVo> getAlbumStatVo(@PathVariable Long albumId);
    /**
     * 查询所有一级分类列表
     *
     * @return
     */
    @GetMapping("/category/findAllCategory1")
    public Result<List<BaseCategory1>> getAllCategory1();

    /**
     *  查询用户未购买声音列表
     * @param trackId 选择购买声音ID，作为起始标准计算未购买声音数量
     * @param trackCount 限制查询未购买声音数量
     * @return
     */
    @GetMapping("/trackInfo/findPaidTrackInfoList/{trackId}/{trackCount}")
    public Result<List<TrackInfo>> findWaitBuyTrackInfoList(@PathVariable Long trackId, @PathVariable Integer trackCount);

    /**
     * 查询用户已购买的专辑列表
     * @param trackId
     * @return
     */
    @GetMapping("/trackInfo/findUserTrackPaidList/{trackId}")
    public Result<List<Map<String, Object>>> getUserTrackPaidList(@PathVariable Long trackId);

    /**
     * 根据声音ID查询声音信息
     * @param id
     * @return
     */
    @GetMapping("/trackInfo/getTrackInfo/{id}")
    public Result<TrackInfo> getTrackInfo(@PathVariable Long id);
}
