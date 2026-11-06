package com.atguigu.tingshu.album.service.impl;

import cn.hutool.core.codec.Base64;
import com.atguigu.tingshu.album.config.VodConstantProperties;
import com.atguigu.tingshu.album.service.AuditService;
import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.ims.v20201229.ImsClient;
import com.tencentcloudapi.ims.v20201229.models.ImageModerationRequest;
import com.tencentcloudapi.ims.v20201229.models.ImageModerationResponse;
import com.tencentcloudapi.tms.v20201229.TmsClient;
import com.tencentcloudapi.tms.v20201229.models.TextModerationRequest;
import com.tencentcloudapi.tms.v20201229.models.TextModerationResponse;
import com.tencentcloudapi.vod.v20180717.VodClient;
import com.tencentcloudapi.vod.v20180717.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author: atguigu
 * @create: 2025-05-29 14:12
 */
@Slf4j
@Service
public class AuditServiceImpl implements AuditService {

    @Autowired
    private Credential credential;

    @Autowired
    private VodConstantProperties vodConstantProperties;

    /**
     * 文本内容审核
     *
     * @param content
     * @return
     */
    @Override
    public String auditText(String content) {
        try {
            //1.实例化要请求产品的client对象,clientProfile是可选的
            TmsClient client = new TmsClient(credential, "ap-beijing");
            //2.实例化一个请求对象,每个接口都会对应一个request对象
            TextModerationRequest req = new TextModerationRequest();
            //对审核内容进行Base64编码
            req.setContent(Base64.encode(content));
            //3.返回的resp是一个TextModerationResponse的实例，与请求对象对应
            TextModerationResponse resp = client.TextModeration(req);
            //4.解析文本审核结果
            if (resp != null) {
                String suggestion = resp.getSuggestion();
                return suggestion.toLowerCase();
            }
        } catch (TencentCloudSDKException e) {
            log.error("文本内容:审核失败", content);
            System.out.println(e.toString());
        }
        return null;
    }

    /**
     * 图片内容审核
     *
     * @param file
     * @return
     */
    @Override
    public String auditImage(MultipartFile file) {
        try {
            //1.实例化要请求产品的client对象,clientProfile是可选的
            ImsClient client = new ImsClient(credential, "ap-beijing");
            //2.实例化一个请求对象,每个接口都会对应一个request对象
            ImageModerationRequest req = new ImageModerationRequest();
            //对图片进行Base64编码
            req.setFileContent(Base64.encode(file.getInputStream()));
            //3.返回的resp是一个ImageModerationResponse的实例，与请求对象对应
            ImageModerationResponse resp = client.ImageModeration(req);
            if (resp != null) {
                String suggestion = resp.getSuggestion();
                return suggestion.toLowerCase();
            }
        } catch (Exception e) {
            log.error("图片内容,{}审核失败", file.getOriginalFilename());
        }
        return null;
    }

    /**
     * 启动审核任务
     *
     * @param mediaFileId
     * @return
     */
    @Override
    public String startReviewTask(String mediaFileId) {
        try {
            //1. 实例化要请求产品的client对象,clientProfile是可选的
            VodClient client = new VodClient(credential, "ap-beijing");
            //2.实例化一个请求对象,每个接口都会对应一个request对象
            ReviewAudioVideoRequest req = new ReviewAudioVideoRequest();
            req.setFileId(mediaFileId);
            //3.返回的resp是一个ReviewAudioVideoResponse的实例，与请求对象对应
            ReviewAudioVideoResponse resp = client.ReviewAudioVideo(req);
            if (resp != null) {
                return resp.getTaskId();
            }
        } catch (TencentCloudSDKException e) {
            log.error("启动审核任务失败", e);
        }
        return null;
    }

    /**
     * 根据审核任务ID查询审核建议
     *
     * @param taskId
     * @return
     */
    @Override
    public String getReviewTaskResult(String taskId) {
        try {
            //1.实例化要请求产品的client对象,clientProfile是可选的
            VodClient client = new VodClient(credential,"ap-beijing");
            //2.实例化一个请求对象,每个接口都会对应一个request对象
            DescribeTaskDetailRequest req = new DescribeTaskDetailRequest();
            req.setTaskId(taskId);
            //3.返回的resp是一个DescribeTaskDetailResponse的实例，与请求对象对应
            DescribeTaskDetailResponse resp = client.DescribeTaskDetail(req);
            if (resp != null) {
                //3.1 获取任务类型：音视频审核任务；
                if ("ReviewAudioVideo".equals(resp.getTaskType())) {
                    //3.2 判断任务状态： 已完成
                    if ("FINISH".equals(resp.getStatus())) {
                        //3.3 获取音视频审核结果
                        ReviewAudioVideoTask reviewAudioVideoTask = resp.getReviewAudioVideoTask();
                        //3.3.1 判断审核任务状态 FINISH
                        if ("FINISH".equals(reviewAudioVideoTask.getStatus())) {
                            //3.3.2 音视频审核任务输出 建议结果
                            ReviewAudioVideoTaskOutput output = reviewAudioVideoTask.getOutput();
                            String suggestion = output.getSuggestion();
                            return suggestion.toLowerCase();
                        }
                    }
                }
            }
        } catch (TencentCloudSDKException e) {
            System.out.println(e.toString());
        }
        return null;
    }


}

