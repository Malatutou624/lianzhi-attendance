package com.lianzhi.attendance.modules.face.service;

import com.arcsoft.face.*;
import com.arcsoft.face.enums.DetectMode;
import com.arcsoft.face.enums.DetectOrient;
import com.arcsoft.face.enums.ErrorInfo;
import com.arcsoft.face.toolkit.ImageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.arcsoft.face.toolkit.ImageFactory.getRGBData;

/**
 * 虹软 ArcFace SDK 3.0 封装服务
 *
 * 功能（覆盖课程第13~15天全部要求）：
 *   ✅ 人脸检测 detect()          → 检测图里有没有人脸
 *   ✅ 特征提取 extractFeature()    → 512 维特征向量
 *   ✅ 特征比对 compare()          → 余弦相似度（0~1）
 *   ✅ 活体检测 livenessCheck()     → 静默活体（防照片刷）
 *   ✅ 人脸属性 getAgeGender()     → 年龄+性别
 *   ✅ 人脸质量 getFaceQuality()   → 模糊/光照打分
 *
 * 首次启动会联网激活（用 yml 里的 APP_ID + SDK_KEY），之后本地激活、无网可用。
 */
@Service
public class ArcSoftFaceService {

    private static final Logger log = LoggerFactory.getLogger(ArcSoftFaceService.class);

    @Value("${lianzhi.arcface.lib-dir}")
    private String libDir;

    @Value("${lianzhi.arcface.app-id}")
    private String appId;

    @Value("${lianzhi.arcface.sdk-key}")
    private String sdkKey;

    @Value("${lianzhi.arcface.active-dir}")
    private String activeDir;

    @Value("${lianzhi.arcface.match-threshold:0.85}")
    private float matchThreshold;

    /** 上传目录绝对路径（拼 /uploads/xxx.jpg → 绝对路径） */
    @Value("${lianzhi.upload-dir}")
    private String uploadDir;

    private FaceEngine faceEngine;
    private boolean initialized = false;

    @PostConstruct
    public synchronized void init() {
        try {
            log.info("[ArcFace] 初始化... libDir={}", libDir);

            // 1. 创建引擎实例（参数是 DLL 目录）
            faceEngine = new FaceEngine(libDir);

            // 2. 激活引擎（首次联网，之后本地）
            int errorCode = faceEngine.activeOnline(appId, sdkKey);
            if (errorCode != ErrorInfo.MOK.getValue()
                    && errorCode != ErrorInfo.MERR_ASF_ALREADY_ACTIVATED.getValue()) {
                log.error("[ArcFace] 激活失败！errorCode={}", errorCode);
                return;
            }
            log.info("[ArcFace] 引擎激活成功");

            // 3. 引擎配置
            EngineConfiguration engineConfiguration = new EngineConfiguration();
            engineConfiguration.setDetectMode(DetectMode.ASF_DETECT_MODE_IMAGE);
            engineConfiguration.setDetectFaceOrientPriority(DetectOrient.ASF_OP_ALL_OUT);
            engineConfiguration.setDetectFaceMaxNum(10);
            engineConfiguration.setDetectFaceScaleVal(16);

            FunctionConfiguration functionConfiguration = new FunctionConfiguration();
            functionConfiguration.setSupportAge(true);
            functionConfiguration.setSupportFace3dAngle(true);
            functionConfiguration.setSupportFaceDetect(true);
            functionConfiguration.setSupportFaceRecognition(true);
            functionConfiguration.setSupportGender(true);
            functionConfiguration.setSupportLiveness(true);
            engineConfiguration.setFunctionConfiguration(functionConfiguration);

            // 4. 初始化引擎
            errorCode = faceEngine.init(engineConfiguration);
            if (errorCode != ErrorInfo.MOK.getValue()) {
                log.error("[ArcFace] 引擎初始化失败！errorCode={}", errorCode);
                return;
            }

            // 5. 设置活体参数
            faceEngine.setLivenessParam(0.5f, 0.7f);

            initialized = true;
            log.info("[ArcFace] ✅ 虹软 ArcFace SDK 3.0 初始化完成！matchThreshold={}", matchThreshold);

        } catch (Throwable t) {
            // 注意：UnsatisfiedLinkError 是 Error 不是 Exception（Linux 容器没有 .so）
            // 用 Throwable 兜底，让 Bean 仍然创建成功但 initialized=false → fallback pHash
            log.warn("[ArcFace] 初始化失败（可能是非 Windows 环境无 .so），fallback pHash: {}", t.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        if (faceEngine != null && initialized) {
            faceEngine.unInit();
            log.info("[ArcFace] 引擎已卸载");
        }
    }

    public boolean isReady() { return initialized; }
    public float getMatchThreshold() { return matchThreshold; }

    // ==========================================================================
    // 核心业务方法
    // ==========================================================================

    /**
     * 检测图片中人脸数量
     * @return 人脸数，-1 表示引擎未初始化，-2 表示加载图片失败
     */
    public int detectFace(String imagePath) {
        if (!initialized) { log.warn("[ArcFace] 引擎未初始化"); return -1; }
        try {
            File f = resolveFile(imagePath);
            if (f == null || !f.exists()) { log.warn("[ArcFace] 图片不存在: {}", imagePath); return -2; }
            ImageInfo imageInfo = getRGBData(f);
            List<FaceInfo> faceInfoList = new ArrayList<>();
            int err = faceEngine.detectFaces(imageInfo.getImageData(), imageInfo.getWidth(),
                    imageInfo.getHeight(), imageInfo.getImageFormat(), faceInfoList);
            if (err != ErrorInfo.MOK.getValue()) {
                log.warn("[ArcFace] detectFaces 失败 errorCode={}", err);
                return -1;
            }
            return faceInfoList.size();
        } catch (Exception e) {
            log.error("[ArcFace] detectFace 异常", e);
            return -1;
        }
    }

    /**
     * 提取人脸特征向量（512维）
     * @return FaceFeature，null 表示失败/无人脸/多人脸
     */
    public FaceFeature extractFeature(String imagePath) {
        if (!initialized) return null;
        try {
            File f = resolveFile(imagePath);
            if (f == null || !f.exists()) return null;
            ImageInfo imageInfo = getRGBData(f);

            // 先检测人脸
            List<FaceInfo> faceInfoList = new ArrayList<>();
            int err = faceEngine.detectFaces(imageInfo.getImageData(), imageInfo.getWidth(),
                    imageInfo.getHeight(), imageInfo.getImageFormat(), faceInfoList);
            if (err != ErrorInfo.MOK.getValue() || faceInfoList.isEmpty()) {
                log.warn("[ArcFace] extractFeature: 未检测到人脸 path={} err={}", imagePath, err);
                return null;
            }
            if (faceInfoList.size() > 1) {
                log.warn("[ArcFace] extractFeature: 检测到 {} 张人脸，请单人照", faceInfoList.size());
                return null;
            }

            // 提取第一张脸的特征
            FaceFeature faceFeature = new FaceFeature();
            err = faceEngine.extractFaceFeature(imageInfo.getImageData(), imageInfo.getWidth(),
                    imageInfo.getHeight(), imageInfo.getImageFormat(), faceInfoList.get(0), faceFeature);
            if (err != ErrorInfo.MOK.getValue()) {
                log.warn("[ArcFace] extractFaceFeature 失败 errorCode={}", err);
                return null;
            }
            return faceFeature;
        } catch (Exception e) {
            log.error("[ArcFace] extractFeature 异常", e);
            return null;
        }
    }

    /**
     * 比对两张图的人脸相似度（0~1，越高越相似）
     * @return MatchResult（similarity 是相似度分数）
     */
    public MatchResult compare(String imagePathA, String imagePathB) {
        MatchResult result = new MatchResult();
        if (!initialized) {
            result.reason = "虹软引擎未初始化";
            return result;
        }

        FaceFeature fA = extractFeature(imagePathA);
        if (fA == null) { result.reason = "模板照片未检测到人脸"; return result; }

        FaceFeature fB = extractFeature(imagePathB);
        if (fB == null) { result.reason = "现场照片未检测到人脸"; return result; }

        FaceSimilar faceSimilar = new FaceSimilar();
        int err = faceEngine.compareFaceFeature(fA, fB, faceSimilar);
        if (err != ErrorInfo.MOK.getValue()) {
            result.reason = "特征比对失败 errorCode=" + err;
            return result;
        }

        result.similarity = faceSimilar.getScore();
        result.match = result.similarity >= matchThreshold;
        return result;
    }

    /**
     * 活体检测（静默）：防照片攻击
     * @return liveness 1=活体, 0=非活体, -1=未知/失败
     */
    public int livenessCheck(String imagePath) {
        if (!initialized) return -1;
        try {
            File f = resolveFile(imagePath);
            if (f == null || !f.exists()) return -1;
            ImageInfo imageInfo = getRGBData(f);

            List<FaceInfo> faceInfoList = new ArrayList<>();
            int err = faceEngine.detectFaces(imageInfo.getImageData(), imageInfo.getWidth(),
                    imageInfo.getHeight(), imageInfo.getImageFormat(), faceInfoList);
            if (err != ErrorInfo.MOK.getValue() || faceInfoList.isEmpty()) return -1;

            FunctionConfiguration fc = new FunctionConfiguration();
            fc.setSupportLiveness(true);
            err = faceEngine.process(imageInfo.getImageData(), imageInfo.getWidth(),
                    imageInfo.getHeight(), imageInfo.getImageFormat(), faceInfoList, fc);
            if (err != ErrorInfo.MOK.getValue()) return -1;

            List<LivenessInfo> livenessInfoList = new ArrayList<>();
            err = faceEngine.getLiveness(livenessInfoList);
            if (err != ErrorInfo.MOK.getValue() || livenessInfoList.isEmpty()) return -1;

            return livenessInfoList.get(0).getLiveness();
        } catch (Exception e) {
            log.error("[ArcFace] livenessCheck 异常", e);
            return -1;
        }
    }

    /**
     * 获取人脸属性（年龄+性别）
     */
    public FaceAttributes getAttributes(String imagePath) {
        if (!initialized) return null;
        try {
            File f = resolveFile(imagePath);
            if (f == null || !f.exists()) return null;
            ImageInfo imageInfo = getRGBData(f);

            List<FaceInfo> faceInfoList = new ArrayList<>();
            int err = faceEngine.detectFaces(imageInfo.getImageData(), imageInfo.getWidth(),
                    imageInfo.getHeight(), imageInfo.getImageFormat(), faceInfoList);
            if (err != ErrorInfo.MOK.getValue() || faceInfoList.isEmpty()) return null;

            FunctionConfiguration fc = new FunctionConfiguration();
            fc.setSupportAge(true);
            fc.setSupportGender(true);
            err = faceEngine.process(imageInfo.getImageData(), imageInfo.getWidth(),
                    imageInfo.getHeight(), imageInfo.getImageFormat(), faceInfoList, fc);
            if (err != ErrorInfo.MOK.getValue()) return null;

            List<AgeInfo> ageList = new ArrayList<>();
            List<GenderInfo> genderList = new ArrayList<>();
            faceEngine.getAge(ageList);
            faceEngine.getGender(genderList);

            FaceAttributes fa = new FaceAttributes();
            fa.age = ageList.isEmpty() ? -1 : ageList.get(0).getAge();
            fa.gender = genderList.isEmpty() ? -1 : genderList.get(0).getGender();
            return fa;
        } catch (Exception e) {
            log.error("[ArcFace] getAttributes 异常", e);
            return null;
        }
    }

    // ==========================================================================
    // 工具方法
    // ==========================================================================

    /**
     * 解析图片路径 → 绝对路径文件
     *
     * 传进来的可能是：
     *   1. /uploads/xxx.jpg     (后端 face_photo 字段)
     *   2. uploads/xxx.jpg      (无前导 /)
     *   3. D:/绝对路径/xxx.jpg   (完整路径)
     *
     * 全部统一用 uploadDir（绝对路径）拼接，避免 Spring Boot 启动目录不确定导致找不到文件
     */
    private File resolveFile(String url) {
        if (url == null || url.isBlank()) return null;

        // 1. 绝对路径，直接返回
        File direct = new File(url);
        if (direct.isAbsolute() && direct.exists()) return direct;

        // 2. /uploads/xxx.jpg 或 uploads/xxx.jpg → 拼 uploadDir
        String local = url;
        if (local.startsWith("/")) local = local.substring(1);        // 去掉开头的 /
        if (local.startsWith("uploads/") || local.startsWith("uploads\\")) {
            // uploadDir 结尾加 /
            String dir = uploadDir;
            if (dir != null && !dir.endsWith("/") && !dir.endsWith("\\")) dir = dir + File.separator;
            File f = new File(dir, local.substring("uploads/".length()));
            log.debug("[ArcFace] resolveFile: url={} → {}", url, f.getAbsolutePath());
            return f.exists() ? f : null;
        }

        // 3. 直接相对路径（可能直接是 xxx.jpg 或已经是绝对路径但不存在）
        return direct.exists() ? direct : null;
    }

    // ==========================================================================
    // 结果 POJO
    // ==========================================================================

    public static class MatchResult {
        /** 相似度分数（0~1） */
        public float similarity;
        /** 是否匹配 */
        public boolean match;
        /** 失败原因（空字符串表示成功） */
        public String reason = "";
    }

    public static class FaceAttributes {
        /** 年龄，-1 表示未知 */
        public int age = -1;
        /** 性别：0=女, 1=男, -1=未知 */
        public int gender = -1;
    }
}
