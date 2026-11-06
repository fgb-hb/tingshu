package com.atguigu.tingshu.album.service.impl;

import cn.hutool.core.io.FileUtil;
import com.atguigu.tingshu.album.config.MinioConstantProperties;
import com.atguigu.tingshu.album.service.AuditService;
import com.atguigu.tingshu.album.service.FileUploadService;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
public class FileUploadServiceImpl implements FileUploadService {


    public static void main(String[] args) {
        String format = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        String s = UUID.randomUUID().toString().replaceAll("-", "");
        String fileName="file"+".jpg";
        String objectName=format+"/"+s+ fileName;
        System.out.println(objectName);
    }

    @Autowired
    private MinioConstantProperties minioConstantProperties;
    @Autowired
    private MinioClient minioClient;
    @Autowired
    private AuditService auditService;
    @Override
    public String fileUpload(MultipartFile multipartFile)  {
        //1业务校验
        try{
            //1.1校验格式
            BufferedImage read = ImageIO.read(multipartFile.getInputStream());
            if (read == null) {
                throw new GuiguException(ResultCodeEnum.FAIL.getCode(), "文件格式错误");
            }
            //1.2校验大小
            int width = read.getWidth();
            int height = read.getHeight();
//            if (width>900 || height>900) {
//                throw new GuiguException(ResultCodeEnum.FAIL.getCode(), "图片大小不能超过900*900");
//            }
            //todo 1.3校验图片是否合规
//            String s = auditService.auditImage(multipartFile);
//            if (!s.equals("pass")) {
//                throw new GuiguException(ResultCodeEnum.FAIL.getCode(), "图片内容不合规");
//            }
            //2生成图片唯一标识
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String fileName = UUID.randomUUID().toString().replaceAll("-", "");
            String originalFilename = multipartFile.getOriginalFilename();
            String suffix = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String objectName = date + "/" + fileName + suffix;

            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(minioConstantProperties.getBucketName())
                    .object(objectName)
                    .stream(multipartFile.getInputStream(), multipartFile.getSize(), -1)
                    .contentType(multipartFile.getContentType())
                    .build();
            minioClient.putObject(putObjectArgs);

            String fileUrl = minioConstantProperties.getEndpointUrl() + "/" + minioConstantProperties.getBucketName() + "/" + objectName;
            log.info("文件上传成功，文件路径：{}", fileUrl);
            return fileUrl;
        } catch (Exception e) {
            log.error("文件上传失败：{}", e.getMessage(), e);
            throw new GuiguException(ResultCodeEnum.FAIL.getCode(), "文件上传失败");
        }
    }
}