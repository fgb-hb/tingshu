package com.atguigu.tingshu.search.client;

import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.search.AlbumInfoIndex;
import com.atguigu.tingshu.search.client.impl.SearchDegradeFeignClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * <p>
 * 搜索模块远程调用API接口
 * </p>
 *
 * @author atguigu
 */
@FeignClient(value = "service-search", fallback = SearchDegradeFeignClient.class, path = "api/search")
public interface SearchFeignClient {
    /**
     * 专辑上架
     *
     * @param albumId 专辑ID
     * @return
     */
    @GetMapping("/albumInfo/upperAlbum/{albumId}")
    public Result upperAlbum(@PathVariable Long albumId);

    /**
     * 专辑下架
     *
     * @param albumId 专辑ID
     * @return
     */
    @GetMapping("/albumInfo/lowerAlbum/{albumId}")
    public Result lowerAlbum(@PathVariable Long albumId);
}