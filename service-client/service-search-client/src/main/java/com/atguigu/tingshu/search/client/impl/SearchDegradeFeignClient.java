package com.atguigu.tingshu.search.client.impl;

import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.search.AlbumInfoIndex;
import com.atguigu.tingshu.search.client.SearchFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author: atguigu
 * @create: 2023-12-05 22:23
 */

@Slf4j
@Component
public class SearchDegradeFeignClient implements SearchFeignClient {
    @Override
    public Result upperAlbum(Long albumId) {
        log.info("专辑上架失败，专辑ID：{}", albumId);
        return Result.fail();
    }

    @Override
    public Result lowerAlbum(Long albumId) {
        log.info("专辑下架失败，专辑ID：{}", albumId);
        return Result.fail();
    }
}