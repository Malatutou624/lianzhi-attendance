package com.lianzhi.attendance.modules.common.controller;

import com.lianzhi.attendance.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.UUID;

/**
 * 文件上传接口：人脸照片、打卡照片都走这里
 * POST /upload  （form-data，字段名 file）
 * 返回可访问的 URL，如 /uploads/xxxx.jpg
 */
@RestController
@RequestMapping("/upload")
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    @Value("${lianzhi.upload-dir}")
    private String uploadDir;

    @PostMapping
    public Result<String> upload(@RequestParam("file") MultipartFile file) throws Exception {
        log.info("[upload] 收到文件: original={}, size={}, uploadDir={}",
                file.getOriginalFilename(), file.getSize(), uploadDir);

        if (file.isEmpty()) {
            return Result.fail("文件为空");
        }
        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf(".")) : ".jpg";
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;

        File dir = new File(uploadDir).getAbsoluteFile();
        if (!dir.exists() && !dir.mkdirs()) {
            log.error("[upload] 目录创建失败: {}", dir.getAbsolutePath());
            return Result.fail("上传目录创建失败：" + dir.getAbsolutePath());
        }
        File target = new File(dir, fileName);
        file.transferTo(target);

        boolean exists = target.exists();
        long size = target.length();
        log.info("[upload] 落盘完成: path={}, exists={}, size={}",
                target.getAbsolutePath(), exists, size);

        return Result.ok("/uploads/" + fileName);
    }
}
