package com.atguigu.tingshu;

import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.atguigu.tingshu.search.service.SearchService;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class test {
    @Autowired
    private AlbumFeignClient albumFeignClient;
    @Autowired
    private UserFeignClient userFeignClient;
    @Autowired
    private SearchService searchService;
    @Autowired
    private RedissonClient redissonClient;
    @Test
    public void test(){
        Result<AlbumInfo> albumInfo = albumFeignClient.getAlbumInfo(1l);
        System.out.println(albumInfo.getData());
    }
    @Test
    public void test1(){
        Result<BaseCategoryView> categoryView = albumFeignClient.getCategoryView(1012l);
        System.out.println(categoryView.getData());
    }
    @Test
    public void test2(){
        Result<UserInfoVo> userInfoVo = userFeignClient.getUserInfoVo(1L);
        System.out.println(userInfoVo.getData()+"==========================");
    }

    @Test
    public void test3(){
        for (int i = 1; i <= 1600; i++) {
            System.out.println(i);
            searchService.upperAlbum(Long.valueOf(i));
        }
    }
    @Test
    public void test4(){
        Result<AlbumInfo> albumInfo = albumFeignClient.getAlbumInfo(304l);
        Long category3Id = albumInfo.getData().getCategory3Id();
        System.out.println(category3Id);
        Result<BaseCategoryView> categoryView = albumFeignClient.getCategoryView(1395l);
        System.out.println(categoryView.getData());
    }
    @Test
    public void test5(){
       for (int i = 1; i <= 1600; i++) {
           redissonClient.getBloomFilter(RedisConstant.ALBUM_BLOOM_FILTER).add(Long.valueOf(i));
       }
    }
}
