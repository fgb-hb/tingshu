package com.atguigu.tingshu.album.task;

import cn.hutool.core.collection.CollUtil;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.service.AuditService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.atguigu.tingshu.common.constant.SystemConstant.*;

/**
 * @author: atguigu
 * @create: 2025-05-29 15:14
 */
@Slf4j
@Component
public class ReviceResultTask {

    @Autowired
    private TrackInfoMapper trackInfoMapper;


    @Autowired
    private AuditService auditService;


    /**
     * 定时获取处于审核中声音，音频内容审核结果
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void reviceResultJob() {
        log.info("开始获取审核结果");
        //1.根据条件：1.审核中状态  2.限制数量 3.查询声音ID跟审核任务ID
        List<TrackInfo> trackInfoList = trackInfoMapper.selectList(
                new LambdaQueryWrapper<TrackInfo>()
                        .eq(TrackInfo::getStatus, SystemConstant.TRACK_STATUS_REVIEWING)
                        .select(TrackInfo::getId, TrackInfo::getReviewTaskId)
                        .orderByAsc(TrackInfo::getId)
                        .last("limit 100")
        );
        //2.遍历声音列表
        if (CollUtil.isNotEmpty(trackInfoList)) {
            //2.1 根据声音表中审核任务ID查询审核结果
            for (TrackInfo trackInfo : trackInfoList) {
                //2.2 根据审核建议更新审核建议
                String suggestion = auditService.getReviewTaskResult(trackInfo.getReviewTaskId());
                if(StringUtils.isNotBlank(suggestion)){
                    if ("pass".equals(suggestion)) {
                        trackInfo.setStatus(TRACK_STATUS_PASS);
                    } else if ("review".equals(suggestion)) {
                        trackInfo.setStatus( SystemConstant.TRACK_STATUS_REVIEWING);
                    } else if ("block".equals(suggestion)) {
                        trackInfo.setStatus(TRACK_STATUS_NO_PASS);
                    }
                    trackInfoMapper.updateById(trackInfo);
                }
            }
        }

    }
}
