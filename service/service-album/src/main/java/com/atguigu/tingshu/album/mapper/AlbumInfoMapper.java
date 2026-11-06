package com.atguigu.tingshu.album.mapper;

import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlbumInfoMapper extends BaseMapper<AlbumInfo> {

    /**
     * 页条件查询当前登录用户发布专辑
     * @param pageParam MP提供分页对象，自动根据页码、页大小计算SQL limit 部分
     * @param albumInfoQuery 查询条件：用户ID，状态，关键字
     * @return
     */
    Page<AlbumListVo> findUserAlbumPage(Page<AlbumListVo> pageParam, @Param("vo") AlbumInfoQuery albumInfoQuery);

    AlbumStatVo getAlbumStatVo(Long albumId);
}
