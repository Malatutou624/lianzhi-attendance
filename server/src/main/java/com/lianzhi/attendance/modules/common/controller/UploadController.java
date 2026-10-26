package com.lianzhi.attendance.modules.common.controller;

import com.lianzhi.attendance.common.Result;
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

    @Value("${lianzhi.upload-dir}")
    private String uploadDir;

    @PostMapping
    public Result<String> upload(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return Result.fail("文件为空");
        }
        // 取原始后缀名（.jpg/.png），文件名换成 UUID 防止重名覆盖
        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf(".")) : ".jpg";
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;

        // 用 getAbsoluteFile() 确保拿到绝对路径，避免相对路径被解析到 Tomcat 临时目录
        File dir = new File(uploadDir).getAbsoluteFile();
        if (!dir.exists() && !dir.mkdirs()) {
            // 目录建不出来时给出明确提示，方便排查（比如路径写错、没有写权限）
            return Result.fail("上传目录创建失败：" + dir.getAbsolutePath());
        }
        File target = new File(dir, fileName);
        file.transferTo(target);

        // WebConfig 里已把 /uploads/** 映射到这个目录
        return Result.ok("/uploads/" + fileName);
    }
}
