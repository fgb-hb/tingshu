package com.atguigu.tingshu.album.mapper;

import com.atguigu.tingshu.model.album.TrackStat;
import com.atguigu.tingshu.vo.album.TrackStatVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TrackStatMapper extends BaseMapper<TrackStat> {


    TrackStatVo getTrackStatVo(Long trackId);
}
