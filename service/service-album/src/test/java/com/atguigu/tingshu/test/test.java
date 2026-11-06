package com.atguigu.tingshu.test;

import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.model.album.AlbumInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class test {
    @Autowired
    private AlbumInfoService albumInfoService;
    @Test
    public void test() {
        AlbumInfo albumInfo = albumInfoService.getAlbumInfo(Long.valueOf(1));
        System.out.println(albumInfo);
    }
}
