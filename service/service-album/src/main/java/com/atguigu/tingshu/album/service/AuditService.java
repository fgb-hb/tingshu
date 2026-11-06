package com.atguigu.tingshu.album.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * @author: atguigu
 * @create: 2025-05-29 14:12
 */
public interface AuditService {


    /**
     * 文本内容审核
     * @param content
     * @return
     */
    String auditText(String content);

    /**
     * 图片内容审核
     * @param file
     * @return
     */
    String auditImage(MultipartFile file);
    /**
     * 启动审核任务，开始对音视频文件进行审核
     * @param mediaFileId
     * @return
     */
    String startReviewTask(String mediaFileId);


    /**
     * 根据审核任务ID查询审核结果
     * @param taskId
     * @return
     */
    String getReviewTaskResult(String taskId);

}
