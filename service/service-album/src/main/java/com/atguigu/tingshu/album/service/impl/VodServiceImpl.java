package com.atguigu.tingshu.album.service.impl;

import com.atguigu.tingshu.album.config.VodConstantProperties;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.util.UploadFileUtil;
import com.atguigu.tingshu.vo.album.TrackMediaInfoVo;
import com.qcloud.vod.VodUploadClient;
import com.qcloud.vod.model.VodUploadRequest;
import com.qcloud.vod.model.VodUploadResponse;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.vod.v20180717.VodClient;
import com.tencentcloudapi.vod.v20180717.models.DeleteMediaRequest;
import com.tencentcloudapi.vod.v20180717.models.DescribeMediaInfosRequest;
import com.tencentcloudapi.vod.v20180717.models.DescribeMediaInfosResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@Service
public class VodServiceImpl implements VodService {

    @Autowired
    private VodConstantProperties vodConstantProperties;
    @Autowired
    private VodUploadClient vodUploadClient;
    @Autowired
    private VodClient vodClient;

    @Override
    public Map<String, String> uploadTrack(MultipartFile file) {
        try {
            //1.将文件信息先保存在本地，返回文件路径mediaFilePath
            String mediaFilePath = UploadFileUtil.uploadTempPath(vodConstantProperties.getTempPath(), file);
            //2.构建文件上传对象
            VodUploadRequest vodUploadRequest = new VodUploadRequest();
            //2.1 设置文件路径
            vodUploadRequest.setMediaFilePath(mediaFilePath);
            //3.上传文件到点播平台
            VodUploadResponse vodUploadResponse = vodUploadClient.upload(vodConstantProperties.getRegion(), vodUploadRequest);
            //4.返回上传结果
            if (vodUploadResponse != null) {
                String mediaUrl = vodUploadResponse.getMediaUrl();
                String fileId = vodUploadResponse.getFileId();
                return Map.of("mediaFileId", fileId, "mediaUrl", mediaUrl);
            }
            return null;
        } catch (Exception e) {
            log.error("上传文件到点播平台失败", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public TrackMediaInfoVo getTrackMediaInfo(String mediaFileId) {
        try {
            //1.构建查询对象
            DescribeMediaInfosRequest req = new DescribeMediaInfosRequest();
            // 支持批量，数组形式，可以传多个FileId
            req.setFileIds(new String[]{mediaFileId});
            DescribeMediaInfosResponse resp = vodClient.DescribeMediaInfos(req);
            // 拿到媒体信息对象
            var mediaInfo = resp.getMediaInfoSet()[0];
            // ========== 1. BasicInfo 基本信息 ==========
            var basicInfo = mediaInfo.getBasicInfo();
            // 文件类型
            String type = basicInfo.getType();
            // ========== 2. MetaData 媒体元信息 ==========
            var metaData = mediaInfo.getMetaData();
            // 总时长（秒）
            Float duration = metaData.getDuration();
            Long size = metaData.getSize();
            //3.返回查询结果
            TrackMediaInfoVo trackMediaInfoVo = new TrackMediaInfoVo();
            trackMediaInfoVo.setSize(size);
            trackMediaInfoVo.setDuration(duration);
            trackMediaInfoVo.setType(type);
            return trackMediaInfoVo;
        } catch (TencentCloudSDKException e) {
            log.error("查询点播平台文件信息失败", e);
            throw new RuntimeException(e);
        }

    }

    /**
     * 删除音视频文件
     *
     * @param mediaFileId
     */
    @Override
    public void deleteMedia(String mediaFileId) {
        try {
            //1.实例化一个认证对象
            Credential cred = new Credential(vodConstantProperties.getSecretId(), vodConstantProperties.getSecretKey());
            //2.实例化要请求产品的client对象,clientProfile是可选的
            VodClient client = new VodClient(cred, vodConstantProperties.getRegion());
            //3.实例化一个请求对象,每个接口都会对应一个request对象
            DeleteMediaRequest req = new DeleteMediaRequest();
            req.setFileId(mediaFileId);
            //4.返回的resp是一个DeleteMediaResponse的实例，与请求对象对应
            client.DeleteMedia(req);
        } catch (TencentCloudSDKException e) {
            log.error("[专辑服务]删除点播平台文件异常：{}", e);
        }
    }


}
